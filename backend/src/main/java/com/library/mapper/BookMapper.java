package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BookMapper extends BaseMapper<Book> {

    /**
     * 对图书行加排他锁（须在事务内调用）。
     * <p>
     * 用于把「同一本书的副本序号分配」串行化：并发新增副本时若不锁，
     * 多个请求会各自算出同一个最大序号，插入时撞 uk_barcode 唯一键。
     */
    @Select("SELECT id FROM book WHERE id = #{bookId} AND is_delete = 0 FOR UPDATE")
    Long lockById(@Param("bookId") Long bookId);
}
