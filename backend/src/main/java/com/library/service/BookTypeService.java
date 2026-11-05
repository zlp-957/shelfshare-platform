package com.library.service;

import com.library.entity.BookType;

import java.util.List;

/**
 * 图书分类服务
 */
public interface BookTypeService {

    List<BookType> listAll();

    void add(BookType bookType);

    void update(BookType bookType);

    void delete(Long id);
}
