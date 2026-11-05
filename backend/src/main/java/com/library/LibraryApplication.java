package com.library;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 书屿 · 共享图书借阅平台 启动类
 */
@SpringBootApplication
@MapperScan("com.library.mapper")
@EnableScheduling
public class LibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
        System.out.println("=======================================");
        System.out.println("  BookLoop 书屿 · 共享图书借阅平台");
        System.out.println("  服务已启动: http://localhost:8080");
        System.out.println("=======================================");
    }
}
