package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.BookItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookItemMapper extends BaseMapper<BookItem> {

    /**
     * 原子切换副本状态（核心幂等闸门）。
     * <p>
     * 借出核销、归还核销、预留、释放都必须走这个方法。
     * 条件里带上「期望的当前状态」，影响行数为 1 才说明本次抢到了处理权，
     * 为 0 说明状态已经被别人改过（重复核销 / 并发抢占），直接返回失败。
     *
     * @param id   副本id
     * @param from 期望的当前状态
     * @param to   目标状态
     * @return 影响行数，1 成功，0 失败
     */
    @Update("UPDATE book_item SET status = #{to}, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{from} AND is_delete = 0")
    int changeStatus(@Param("id") Long id, @Param("from") int from, @Param("to") int to);

    /**
     * 刷新书目的在馆副本数（派生值）。
     * book.stock 不是业务字段，只是 book_item 的统计缓存，每次副本状态变更后调用。
     */
    @Update("UPDATE book SET stock = ("
            + "  SELECT COUNT(*) FROM book_item "
            + "  WHERE book_id = #{bookId} AND status = 0 AND is_delete = 0"
            + ") WHERE id = #{bookId}")
    int refreshStock(@Param("bookId") Long bookId);
}
