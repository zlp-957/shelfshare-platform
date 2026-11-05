package com.library.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.entity.Notice;

import java.util.List;

/**
 * 公告服务
 */
public interface NoticeService {

    Page<Notice> pageNotices(Integer page, Integer size);

    List<Notice> latestNotices();

    void add(Notice notice);

    void update(Notice notice);

    void delete(Long id);
}
