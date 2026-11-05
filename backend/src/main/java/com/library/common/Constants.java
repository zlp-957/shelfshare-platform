package com.library.common;

/**
 * 系统常量
 * <p>
 * 2.0 变更提示：
 * 借阅状态的值全部重定义（旧 0=借阅中，新 0=待取书，语义相反）。
 * 旧常量名以 @Deprecated 保留仅为编译兼容，新代码请用 BORROW_STATUS_* 新常量。
 */
public class Constants {

    /** 用户角色：管理员 */
    public static final int ROLE_ADMIN = 1;
    /** 用户角色：读者 */
    public static final int ROLE_READER = 0;

    /** 用户状态：正常 */
    public static final int USER_STATUS_NORMAL = 0;
    /** 用户状态：禁用 */
    public static final int USER_STATUS_DISABLED = 1;

    // ==================== 借阅状态 ====================

    /** 借阅状态：待取书（已付押金，等待到馆核销，未开始计借期） */
    public static final int BORROW_STATUS_PENDING_PICKUP = 0;
    /** 借阅状态：借阅中（已核销，借期生效） */
    public static final int BORROW_STATUS_BORROWING = 1;
    /** 借阅状态：已归还 */
    public static final int BORROW_STATUS_RETURNED = 2;
    /** 借阅状态：已买断（逾期满约定期或押金扣光，书归用户） */
    public static final int BORROW_STATUS_BOUGHT_OUT = 3;
    /** 借阅状态：已取消（超时未取书 / 用户取消） */
    public static final int BORROW_STATUS_CANCELED = 4;

    // ==================== 副本状态 ====================

    /** 副本状态：在馆（可借） */
    public static final int ITEM_STATUS_IN_LIBRARY = 0;
    /** 副本状态：借出中 */
    public static final int ITEM_STATUS_BORROWED = 1;
    /** 副本状态：已售出 */
    public static final int ITEM_STATUS_SOLD = 2;
    /** 副本状态：已买断 */
    public static final int ITEM_STATUS_BOUGHT_OUT = 3;
    /** 副本状态：遗失 */
    public static final int ITEM_STATUS_LOST = 4;
    /** 副本状态：破损 */
    public static final int ITEM_STATUS_DAMAGED = 5;
    /** 副本状态：下架 */
    public static final int ITEM_STATUS_OFFLINE = 6;
    /** 副本状态：已预留（下单锁定，等待核销） */
    public static final int ITEM_STATUS_RESERVED = 7;

    // ==================== 押金退款状态 ====================

    /** 退款状态：无需退款（未产生退款金额） */
    public static final int REFUND_STATUS_NONE = 0;
    /** 退款状态：已退成功 */
    public static final int REFUND_STATUS_SUCCESS = 1;
    /** 退款状态：渠道调用失败，等待定时任务重试 */
    public static final int REFUND_STATUS_FAILED = 2;

    // ==================== 预约状态 ====================

    /** 预约状态：排队中 */
    public static final int RESERVE_STATUS_QUEUING = 0;
    /** 预约状态：待借阅（已通知） */
    public static final int RESERVE_STATUS_NOTIFIED = 1;
    /** 预约状态：已借阅 */
    public static final int RESERVE_STATUS_BORROWED = 2;
    /** 预约状态：已取消 */
    public static final int RESERVE_STATUS_CANCELED = 3;
    /** 预约状态：已失效 */
    public static final int RESERVE_STATUS_EXPIRED = 4;

    // ==================== 支付单状态 ====================

    /** 支付单：待支付 */
    public static final int PAY_STATUS_UNPAID = 0;
    /** 支付单：已支付 */
    public static final int PAY_STATUS_PAID = 1;
    /** 支付单：已关闭 */
    public static final int PAY_STATUS_CLOSED = 2;
    /** 支付单：已全额退款 */
    public static final int PAY_STATUS_REFUNDED = 3;
    /** 支付单：部分退款 */
    public static final int PAY_STATUS_PARTIAL_REFUNDED = 4;

    // ==================== 支付业务类型 ====================

    /** 支付业务：借阅押金 */
    public static final int PAY_BIZ_DEPOSIT = 1;
    /** 支付业务：购买书款 */
    public static final int PAY_BIZ_PURCHASE = 2;
    /** 支付业务：合并订单（购物车结算，可能同时含借阅押金与购买书款） */
    public static final int PAY_BIZ_COMBINED = 3;

    // ==================== 支付渠道 ====================

    /** 支付渠道：支付宝 */
    public static final int PAY_CHANNEL_ALIPAY = 1;
    /** 支付渠道：模拟 */
    public static final int PAY_CHANNEL_MOCK = 3;

    // ==================== 资金流水 ====================

    /** 流水类型：押金收入 */
    public static final int FLOW_TYPE_DEPOSIT_IN = 1;
    /** 流水类型：书款收入 */
    public static final int FLOW_TYPE_PURCHASE_IN = 2;
    /** 流水类型：押金退还 */
    public static final int FLOW_TYPE_DEPOSIT_REFUND = 3;
    /** 流水类型：逾期扣款 */
    public static final int FLOW_TYPE_OVERDUE_DEDUCT = 4;
    /** 流水类型：破损扣款 */
    public static final int FLOW_TYPE_DAMAGE_DEDUCT = 5;
    /** 流水类型：买断扣款 */
    public static final int FLOW_TYPE_BUYOUT_DEDUCT = 6;
    /** 流水类型：合并订单收入（购物车结算，可能含押金+书款） */
    public static final int FLOW_TYPE_COMBINED_IN = 7;

    /** 资金方向：收入 */
    public static final int FLOW_DIRECTION_IN = 1;
    /** 资金方向：支出 */
    public static final int FLOW_DIRECTION_OUT = 2;

    // ==================== 购买类型 ====================

    /** 购买类型：直接购买（2.0 只有这一种，保留字段便于兼容历史订单） */
    public static final int BUY_TYPE_DIRECT = 0;

    // ==================== 购买取书状态 ====================

    /** 取书状态：待取书（已付款，书仍在馆，此阶段可取消退款） */
    public static final int PICKUP_STATUS_PENDING = 0;
    /** 取书状态：已取书（到馆核销完成，交易结束，不再支持退款） */
    public static final int PICKUP_STATUS_DONE = 1;

    // ==================== 用户取书码 ====================

    /** 取书码有效期（分钟）。过期后需读者重新生成 */
    public static final int PICKUP_CODE_EXPIRE_MINUTES = 30;

    /**
     * 同一终端在时间窗口内允许的取书码查询失败次数。
     * <p>
     * 原来按"输错的码"计数并试图作废它——那个码根本不存在，作废是空操作，
     * 换个码接着试就绕过了。真正的暴力枚举是"每次换一个码试"，
     * 所以只能按终端（IP）计数才有意义。
     */
    public static final int PICKUP_QUERY_MAX_FAIL = 20;

    /** 取书码查询失败达到上限后的锁定时长（分钟） */
    public static final int PICKUP_QUERY_LOCK_MINUTES = 10;

    /** 密码重置码连续输错上限，达到即作废该次申请 */
    public static final int RESET_CODE_MAX_FAIL = 5;

    // ==================== 规则协议 ====================

    /** 协议类型：借阅规则 */
    public static final int AGREEMENT_TYPE_BORROW = 1;
    /** 协议类型：购买须知 */
    public static final int AGREEMENT_TYPE_PURCHASE = 2;
    /** 协议类型：用户协议 */
    public static final int AGREEMENT_TYPE_USER = 3;

    // ==================== 密码重置申请 ====================

    /** 重置申请：待处理（管理员尚未核实） */
    public static final int RESET_STATUS_PENDING = 0;
    /** 重置申请：已发放重置码，等待用户使用 */
    public static final int RESET_STATUS_CODE_SENT = 1;
    /** 重置申请：已完成（用户已改密） */
    public static final int RESET_STATUS_DONE = 2;
    /** 重置申请：已拒绝 / 重置码已过期 */
    public static final int RESET_STATUS_REJECTED = 3;

    /** 重置码有效期（分钟）。管理员口头告知用户后，超时作废需重新申请 */
    public static final int RESET_CODE_EXPIRE_MINUTES = 30;

    /** 同一手机号两次提交找回申请的间隔（分钟），防止反复刷单 */
    public static final int RESET_APPLY_COOLDOWN_MINUTES = 1;

    // ==================== 账号与密码 ====================

    /** 账号格式正则：读者 R + 8位数字 */
    public static final String REGEX_READER_ACCOUNT = "^R\\d{8}$";
    /** 账号格式正则：管理员 A + 6位数字 */
    public static final String REGEX_ADMIN_ACCOUNT = "^A\\d{6}$";

    /** 密码最小长度（注册、新增读者、修改密码统一） */
    public static final int PASSWORD_MIN_LENGTH = 6;
    /** 密码最大长度 */
    public static final int PASSWORD_MAX_LENGTH = 32;

    /** 登录失败次数上限，超过后临时锁定 */
    public static final int LOGIN_MAX_FAIL_COUNT = 5;
    /** 登录锁定时长（分钟） */
    public static final int LOGIN_LOCK_MINUTES = 15;
}
