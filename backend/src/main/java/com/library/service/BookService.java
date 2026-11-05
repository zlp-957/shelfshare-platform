package com.library.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.entity.Book;
import com.library.vo.BookDetailVO;
import com.library.vo.BookVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图书服务
 */
public interface BookService {

    /**
     * 分页查询图书（读者端，含分类名）
     */
    Page<BookVO> pageBooks(Integer page, Integer size, String keyword, Long typeId);

    /**
     * 图书详情
     */
    BookVO getById(Long id);

    /**
     * 图书详情（含当前用户对该书的借阅/预约状态，供读者端按钮判断）
     */
    BookDetailVO getDetail(Long id);

    /**
     * 新增图书
     */
    void add(Book book);

    /**
     * 编辑图书
     */
    void update(Book book);

    /**
     * 删除图书
     */
    void delete(Long id);

    /**
     * 上传封面，返回 URL
     */
    String uploadCover(MultipartFile file);
}
