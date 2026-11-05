package com.library.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.vo.BookItemVO;

import java.util.List;

/**
 * 图书副本服务
 * <p>
 * 副本是 2.0 的核心：每册实体书独立条码，核销靠条码定位到具体哪一册。
 */
public interface BookItemService {

    /** 为某本书批量新增副本（自动生成条码） */
    void addItems(Long bookId, int count, String location);

    /** 查询某本书的全部副本 */
    List<BookItemVO> listByBook(Long bookId);

    /** 管理员分页查询副本 */
    Page<BookItemVO> page(Integer page, Integer size, Long bookId, Integer status, String keyword);

    /** 管理员标记副本状态（遗失 / 破损 / 下架） */
    void updateStatus(Long itemId, Integer status, String remark);

    /**
     * 按条码查副本（核销用）
     */
    BookItemVO getByBarcode(String barcode);
}
