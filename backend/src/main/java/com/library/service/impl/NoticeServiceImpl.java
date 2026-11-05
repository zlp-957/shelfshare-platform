package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BusinessException;
import com.library.common.PageUtil;
import com.library.entity.Notice;
import com.library.mapper.NoticeMapper;
import com.library.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 公告服务实现
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    @Autowired
    private NoticeMapper noticeMapper;

    @Override
    public Page<Notice> pageNotices(Integer page, Integer size) {
        return noticeMapper.selectPage(PageUtil.of(page, size),
                new LambdaQueryWrapper<Notice>().orderByDesc(Notice::getCreateTime));
    }

    @Override
    public List<Notice> latestNotices() {
        return noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .orderByDesc(Notice::getCreateTime)
                .last("limit 5"));
    }

    @Override
    public void add(Notice notice) {
        if (!StringUtils.hasText(notice.getTitle())) {
            throw new BusinessException("公告标题不能为空");
        }
        noticeMapper.insert(notice);
    }

    @Override
    public void update(Notice notice) {
        if (notice.getId() == null) {
            throw new BusinessException("公告id不能为空");
        }
        noticeMapper.updateById(notice);
    }

    @Override
    public void delete(Long id) {
        noticeMapper.deleteById(id);
    }
}
