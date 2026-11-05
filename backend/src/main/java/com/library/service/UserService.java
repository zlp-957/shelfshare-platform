package com.library.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.entity.User;
import com.library.vo.RegisterVO;

/**
 * 用户服务
 */
public interface UserService {

    /**
     * 分页查询读者
     */
    Page<User> pageReaders(Integer page, Integer size, String keyword);

    /**
     * 管理员新增读者
     */
    void addReader(RegisterVO vo);

    /**
     * 管理员编辑读者
     *
     * @param phone 手机号，传 null 表示不修改；传空串表示清空
     */
    void updateReader(Long id, String name, String password, String phone);

    /**
     * 启用/禁用读者
     */
    void toggleStatus(Long id);

    /**
     * 获取当前登录用户信息
     */
    User getCurrentUser();

    /**
     * 修改当前用户信息
     */
    void updateProfile(String name);

    /**
     * 修改密码
     */
    void updatePassword(String oldPassword, String newPassword);

    /**
     * 更新头像地址
     */
    void updateAvatar(String avatarUrl);

    /**
     * 更新手机号（仅 11 位格式校验，不验证、不要求唯一）
     */
    void updatePhone(String phone);
}
