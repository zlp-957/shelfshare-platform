package com.library.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.vo.PasswordResetVO;

/**
 * 密码重置服务
 * <p>
 * 完整流程：读者提交申请 → 管理员电话核实 → 发放一次性重置码 → 读者自行改密。
 * 管理员全程不接触用户的新密码。
 */
public interface PasswordResetService {

    /**
     * 读者提交找回申请（姓名 + 手机号须与账号绑定信息一致，否则不建单）
     */
    void apply(String name, String phone);

    /**
     * 管理端分页查询申请
     */
    Page<PasswordResetVO> page(Integer page, Integer size, Integer status);

    /**
     * 管理员核实后发放一次性重置码
     *
     * @return 6 位重置码，仅在本次返回，用于管理员口头告知用户
     */
    String generateCode(Long id);

    /**
     * 管理员拒绝申请
     */
    void reject(Long id, String remark);

    /**
     * 读者凭重置码设置新密码
     */
    void resetPassword(String account, String code, String newPassword);

    /**
     * 待处理申请数量（管理端角标）
     */
    Long pendingCount();

    /**
     * 定时清理：把重置码已过期的「已发码」申请置为「已拒绝/过期」。
     * <p>
     * 不清理的话，这些单会一直挂在「已发码」，读者重新申请被挡、
     * 管理员发码和拒绝也都被状态校验拦住，只能改库才能恢复。
     *
     * @return 本次作废的申请数
     */
    int expireStale();
}
