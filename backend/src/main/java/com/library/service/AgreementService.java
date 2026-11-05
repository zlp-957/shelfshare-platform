package com.library.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.BusinessException;
import com.library.entity.UserAgreement;
import com.library.mapper.UserAgreementMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则确认服务
 * <p>
 * 押金、逾期、买断这套规则必须在用户掏钱之前讲清楚，并且留痕。
 * 只有前端弹窗是不够的：用户可以直接调接口绕过，所以业务入口必须强制校验。
 */
@Slf4j
@Service
public class AgreementService {

    /** 协议不支持时的默认兜底文案 */
    private static final String FALLBACK = "规则内容加载失败，请联系管理员。";

    @Autowired
    private UserAgreementMapper userAgreementMapper;

    @Value("${library.agreement.version:2.0}")
    private String version;

    public String currentVersion() {
        return version;
    }

    /**
     * 读取规则正文（从 classpath，改文案不用重新打包前端）
     */
    public String loadContent(int type) {
        String file = type == com.library.common.Constants.AGREEMENT_TYPE_PURCHASE
                ? "agreement/purchase-rule.md"
                : "agreement/borrow-rule.md";
        try {
            ClassPathResource resource = new ClassPathResource(file);
            try (InputStream in = resource.getInputStream()) {
                return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("读取规则文件失败 file={}", file, e);
            return FALLBACK;
        }
    }

    /**
     * 是否已同意当前版本
     */
    public boolean hasAgreed(Long userId, int type) {
        if (userId == null) {
            return false;
        }
        Long count = userAgreementMapper.selectCount(new LambdaQueryWrapper<UserAgreement>()
                .eq(UserAgreement::getUserId, userId)
                .eq(UserAgreement::getAgreementType, type)
                .eq(UserAgreement::getVersion, version));
        return count != null && count > 0;
    }

    /**
     * 记录同意（幂等：同一用户同一版本只记一条）
     */
    public void agree(Long userId, int type, String clientIp) {
        if (hasAgreed(userId, type)) {
            return;
        }
        UserAgreement record = new UserAgreement();
        record.setUserId(userId);
        record.setAgreementType(type);
        record.setVersion(version);
        record.setAgreedTime(LocalDateTime.now());
        record.setClientIp(clientIp);
        try {
            userAgreementMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发下重复插入，忽略即可
            log.debug("重复同意记录已存在 userId={} type={}", userId, type);
        }
    }

    /**
     * 业务入口强制校验：未同意当前版本则拒绝
     */
    public void assertAgreed(Long userId, int type) {
        if (!hasAgreed(userId, type)) {
            String name = type == com.library.common.Constants.AGREEMENT_TYPE_PURCHASE
                    ? "《购买须知》" : "《借阅规则》";
            throw new BusinessException("请先阅读并同意" + name);
        }
    }

    /**
     * 我的同意记录
     */
    public List<UserAgreement> myAgreements(Long userId) {
        return userAgreementMapper.selectList(new LambdaQueryWrapper<UserAgreement>()
                .eq(UserAgreement::getUserId, userId)
                .orderByDesc(UserAgreement::getAgreedTime));
    }
}
