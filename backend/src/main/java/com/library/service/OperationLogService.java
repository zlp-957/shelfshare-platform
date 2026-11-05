package com.library.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.PageUtil;
import com.library.entity.OperationLog;
import com.library.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 操作审计日志服务
 */
@Slf4j
@Service
public class OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    /**
     * 写一条审计日志。
     * <p>
     * 必须用独立事务：业务方法抛异常时它的事务已经回滚，
     * 如果日志跟着进同一个事务，「操作失败」这件事恰恰记不下来——
     * 而失败记录往往比成功记录更值得查。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void save(OperationLog logEntity) {
        operationLogMapper.insert(logEntity);
    }

    /**
     * 管理端分页查询
     */
    public Page<OperationLog> page(Integer page, Integer size, String module, String keyword) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(OperationLog::getUserAccount, kw)
                    .or().like(OperationLog::getAction, kw)
                    .or().like(OperationLog::getDetail, kw));
        }
        wrapper.orderByDesc(OperationLog::getId);
        return operationLogMapper.selectPage(PageUtil.of(page, size), wrapper);
    }
}
