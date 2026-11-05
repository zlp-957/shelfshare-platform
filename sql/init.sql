-- ============================================
-- 书屿 · 共享图书借阅平台 数据库初始化脚本
-- 数据库：library
-- 说明：免费借阅 + 付费购买 + 预约排队
-- ============================================

CREATE DATABASE IF NOT EXISTS `library` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `library`;

-- --------------------------------------------
-- 1. user 用户表
-- --------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account`       VARCHAR(32)  NOT NULL COMMENT '账号：读者R+8位数字 / 管理员A+6位数字',
  `password`      VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `name`          VARCHAR(50)  NOT NULL COMMENT '姓名',
  `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号（仅采集，不验证）',
  `pickup_code`   VARCHAR(6)   DEFAULT NULL COMMENT '取书码（6位，30分钟有效，核销后作废）',
  `pickup_code_expire` DATETIME DEFAULT NULL COMMENT '取书码过期时间',
  `role`          TINYINT      NOT NULL DEFAULT 0 COMMENT '角色：0读者 1管理员',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0正常 1禁用',
  `token_version` INT          NOT NULL DEFAULT 0 COMMENT '令牌版本：改密时自增，使旧 JWT 失效',
  `is_delete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`   DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`),
  UNIQUE KEY `uk_user_pickup_code` (`pickup_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- --------------------------------------------
-- 2. book_type 图书分类表
-- --------------------------------------------
DROP TABLE IF EXISTS `book_type`;
CREATE TABLE `book_type` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_delete`   TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME    DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书分类表';

-- --------------------------------------------
-- 3. book 图书表
-- --------------------------------------------
DROP TABLE IF EXISTS `book`;
CREATE TABLE `book` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(100)  NOT NULL COMMENT '书名',
  `author`      VARCHAR(50)   DEFAULT NULL COMMENT '作者',
  `isbn`        VARCHAR(32)   DEFAULT NULL COMMENT 'ISBN',
  `type_id`     BIGINT        DEFAULT NULL COMMENT '分类id',
  `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '售价',
  `stock`       INT           NOT NULL DEFAULT 0 COMMENT '在馆副本数（派生值，由 book_item 统计维护）',
  `cover`       VARCHAR(255)  DEFAULT NULL COMMENT '封面图片地址',
  `intro`       TEXT          DEFAULT NULL COMMENT '简介',
  `is_delete`   TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME      DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type_id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书表';

-- --------------------------------------------
-- 4. borrow 借阅记录表
-- --------------------------------------------
DROP TABLE IF EXISTS `borrow`;
CREATE TABLE `borrow` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT        NOT NULL COMMENT '借阅用户id',
  `book_id`        BIGINT        NOT NULL COMMENT '图书id（书目）',
  `item_id`        BIGINT        DEFAULT NULL COMMENT '副本id（核销时确定）',
  `borrow_time`    DATETIME      NOT NULL COMMENT '下单支付时间',
  `verify_time`    DATETIME      DEFAULT NULL COMMENT '借出核销时间（借期起点）',
  `deadline_time`  DATETIME      NOT NULL COMMENT '应还时间',
  `deposit_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '押金金额',
  `deduct_amount`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已扣押金（逾期+破损）',
  `refund_amount`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已退押金',
  `refund_status`  TINYINT       NOT NULL DEFAULT 0 COMMENT '押金退款状态：0无需退款 1已退成功 2失败待重试',
  `refund_fail_time` DATETIME    DEFAULT NULL COMMENT '退款失败时刻（滞留计时起点，不被其他更新触碰）',
  `pay_order_no`   VARCHAR(32)   DEFAULT NULL COMMENT '押金支付单号',
  `pickup_code`    VARCHAR(6)    DEFAULT NULL COMMENT '取书码（6位数字，支付后生成）',
  `return_time`    DATETIME      DEFAULT NULL COMMENT '实际归还时间',
  `status`         TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0待取书 1借阅中 2已归还 3已买断 4已取消',
  `is_overdue`     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否逾期：0否 1是',
  `create_time`    DATETIME      DEFAULT NULL COMMENT '创建时间',
  `update_time`    DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_book` (`book_id`),
  KEY `idx_item` (`item_id`),
  KEY `idx_pay_order` (`pay_order_no`),
  UNIQUE KEY `uk_pickup_code` (`pickup_code`),
  KEY `idx_status_deadline` (`status`, `deadline_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借阅记录表';

-- --------------------------------------------
-- 5. purchase_order 购买订单表
-- --------------------------------------------
DROP TABLE IF EXISTS `purchase_order`;
CREATE TABLE `purchase_order` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT        NOT NULL COMMENT '购买用户id',
  `book_id`      BIGINT        NOT NULL COMMENT '图书id',
  `item_id`      BIGINT        DEFAULT NULL COMMENT '副本id（购买锁定的是具体某一册）',
  `order_no`     VARCHAR(40)   NOT NULL COMMENT '订单编号',
  `pay_order_no` VARCHAR(32)   DEFAULT NULL COMMENT '支付单号',
  `buy_type`     TINYINT       NOT NULL DEFAULT 0 COMMENT '购买类型：0直接购买',
  `price`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '成交价格',
  `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0待支付 1已支付 2已取消',
  `pickup_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '取书状态：0待取书 1已取书（仅 status=1 时有效）',
  `pickup_time`  DATETIME      DEFAULT NULL COMMENT '取书核销时间',
  `is_delete`    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time`  DATETIME      DEFAULT NULL COMMENT '下单时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`),
  KEY `idx_book` (`book_id`),
  KEY `idx_item` (`item_id`),
  KEY `idx_pay_order` (`pay_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购买订单表';

-- --------------------------------------------
-- 6. book_reserve 图书预约表
-- --------------------------------------------
DROP TABLE IF EXISTS `book_reserve`;
CREATE TABLE `book_reserve` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT   NOT NULL COMMENT '预约用户id',
  `book_id`        BIGINT   NOT NULL COMMENT '图书id',
  `reserve_time`   DATETIME NOT NULL COMMENT '预约时间（FIFO排队依据）',
  `status`         TINYINT  NOT NULL DEFAULT 0 COMMENT '状态：0排队中 1待借阅 2已借阅 3已取消 4已失效',
  `valid_deadline` DATETIME DEFAULT NULL COMMENT '通知后有效期限（3天内需借阅）',
  `is_delete`      TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time`    DATETIME DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_book_status_time` (`book_id`, `status`, `reserve_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书预约表';

-- --------------------------------------------
-- 7. notice 公告表
-- --------------------------------------------
DROP TABLE IF EXISTS `notice`;
CREATE TABLE `notice` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`       VARCHAR(100) NOT NULL COMMENT '公告标题',
  `content`     TEXT         DEFAULT NULL COMMENT '公告内容',
  `is_delete`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- --------------------------------------------
-- 8. book_item 图书副本表（每册实体书一条记录）
-- --------------------------------------------
DROP TABLE IF EXISTS `book_item`;
CREATE TABLE `book_item` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `book_id`     BIGINT       NOT NULL COMMENT '所属书目id',
  `barcode`     VARCHAR(32)  NOT NULL COMMENT '副本条码（馆内唯一，贴在书内页）',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0在馆 1借出中 2已售出 3已买断 4遗失 5破损 6下架 7已预留',
  `location`    VARCHAR(64)  DEFAULT NULL COMMENT '馆内位置',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_delete`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME     DEFAULT NULL COMMENT '入库时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_barcode` (`barcode`),
  KEY `idx_book_status` (`book_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书副本表';

-- --------------------------------------------
-- 9. pay_order 支付单表
-- --------------------------------------------
DROP TABLE IF EXISTS `pay_order`;
CREATE TABLE `pay_order` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`      VARCHAR(32)   NOT NULL COMMENT '支付单号（业务幂等键）',
  `user_id`       BIGINT        NOT NULL COMMENT '付款用户id',
  `biz_type`      TINYINT       NOT NULL COMMENT '业务类型：1借阅押金 2购买书款',
  `biz_id`        BIGINT        DEFAULT NULL COMMENT '关联业务id',
  `amount`        DECIMAL(10,2) NOT NULL COMMENT '应付金额',
  `refund_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已退金额（累计）',
  `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0待支付 1已支付 2已关闭 3已全额退款 4部分退款',
  `channel`       TINYINT       NOT NULL DEFAULT 1 COMMENT '渠道：1支付宝 3模拟',
  `trade_no`      VARCHAR(64)   DEFAULT NULL COMMENT '渠道交易号',
  `pay_time`      DATETIME      DEFAULT NULL COMMENT '支付完成时间',
  `expire_time`   DATETIME      NOT NULL COMMENT '订单失效时间',
  `is_delete`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time`   DATETIME      DEFAULT NULL COMMENT '下单时间',
  `update_time`   DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';

-- --------------------------------------------
-- 10. payment_flow 资金流水表（账本，只追加不删改）
-- --------------------------------------------
DROP TABLE IF EXISTS `payment_flow`;
CREATE TABLE `payment_flow` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT        NOT NULL COMMENT '用户id',
  `biz_type`      TINYINT       NOT NULL COMMENT '类型：1押金收入 2书款收入 3押金退还 4逾期扣款 5破损扣款 6买断扣款',
  `biz_id`        BIGINT        DEFAULT NULL COMMENT '关联业务id',
  `biz_no`        VARCHAR(32)   DEFAULT NULL COMMENT '关联单号',
  `direction`     TINYINT       NOT NULL COMMENT '方向：1收入 2支出',
  `amount`        DECIMAL(10,2) NOT NULL COMMENT '金额（正数）',
  `remark`        VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `create_time`   DATETIME      DEFAULT NULL COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水表';

-- --------------------------------------------
-- 11. user_agreement 规则同意记录
-- --------------------------------------------
DROP TABLE IF EXISTS `user_agreement`;
CREATE TABLE `user_agreement` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT      NOT NULL COMMENT '用户id',
  `agreement_type` TINYINT     NOT NULL COMMENT '类型：1借阅规则 2购买须知 3用户协议',
  `version`        VARCHAR(16) NOT NULL COMMENT '协议版本号',
  `agreed_time`    DATETIME    NOT NULL COMMENT '同意时间',
  `client_ip`      VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type_version` (`user_id`, `agreement_type`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则同意记录';

-- ============================================
-- 初始化数据
-- ============================================

-- 管理员账号 admin / 密码 123456（BCrypt 加密）
INSERT INTO `user` (`account`, `password`, `name`, `role`, `status`, `create_time`, `update_time`) VALUES
('admin', '$2b$10$dkJc0poyB2xP7NRDCT4B/uggjXlYH8YLYqSciAyOTlsVLXPKVpCvW', '系统管理员', 1, 0, NOW(), NOW());

-- 示例读者（密码 123456，R20260001 / R20260002）
-- 手机号一并写入：密码找回要校验「姓名 + 手机号」，留空的话演示账号用不了找回功能
INSERT INTO `user` (`account`, `password`, `name`, `phone`, `role`, `status`, `create_time`, `update_time`) VALUES
('R20260001', '$2b$10$dkJc0poyB2xP7NRDCT4B/uggjXlYH8YLYqSciAyOTlsVLXPKVpCvW', '张三', '13800138001', 0, 0, NOW(), NOW()),
('R20260002', '$2b$10$dkJc0poyB2xP7NRDCT4B/uggjXlYH8YLYqSciAyOTlsVLXPKVpCvW', '李四', '13800138002', 0, 0, NOW(), NOW());

-- 图书分类
INSERT INTO `book_type` (`name`, `remark`, `create_time`) VALUES
('计算机', '计算机类图书', NOW()),
('文学', '文学类图书', NOW()),
('历史', '历史类图书', NOW()),
('科学', '科学类图书', NOW()),
('经济管理', '经济管理类图书', NOW());

-- 示例图书
-- 封面图片已预置于项目根 uploads/ 目录（来源：豆瓣图书公开封面）
INSERT INTO `book` (`name`, `author`, `isbn`, `type_id`, `price`, `stock`, `cover`, `intro`, `create_time`, `update_time`) VALUES
('Java核心技术 卷I（原书第12版）', '凯·S.霍斯特曼', '9787111706410', 1, 149.00, 5, '/uploads/2eb4205ad1bf4bafa793b155c7d20a1e.jpg', 'Java领域经典著作，全面讲解Java语言核心概念。', NOW(), NOW()),
('深入理解Java虚拟机', '周志明', '9787111641247', 1, 129.00, 3, '/uploads/326313d777004e7c8e3f72136553c6f8.jpg', 'JVM原理与调优的必读之作。', NOW(), NOW()),
('Spring实战（第6版）', '克雷格·沃斯', '9787115598691', 1, 109.80, 4, '/uploads/7e4946180da84cd5af2605f44549bf32.jpg', 'Spring框架的权威指南。', NOW(), NOW()),
('活着', '余华', '9787506365437', 2, 20.00, 8, '/uploads/aee168564a8e44e4a58368174315ab33.jpg', '讲述一个人和他的命运之间的友情。', NOW(), NOW()),
('百年孤独', '加西亚·马尔克斯', '9787544253994', 2, 39.50, 6, '/uploads/937dd082373e4b95bc2496a2438221d6.jpg', '魔幻现实主义文学的代表作。', NOW(), NOW()),
('三体', '刘慈欣', '9787536692930', 2, 23.00, 0, '/uploads/0e90d9b4d7594501b8400f0f7bc87740.jpg', '雨果奖获奖作品，中国科幻里程碑。', NOW(), NOW()),
('人类简史', '尤瓦尔·赫拉利', '9787508647357', 3, 68.00, 7, '/uploads/4495ea0d3dce40b19f510591bb6d51d8.jpg', '从动物到上帝，重新审视人类历史。', NOW(), NOW()),
('明朝那些事儿·壹 洪武大帝', '当年明月', '9787505722460', 3, 28.80, 2, '/uploads/24d998492bdf4f73ba66c76a93e29cfd.jpg', '从朱元璋的出身写到永乐夺位的靖难之役，叙述明朝最艰苦卓绝的开国过程。', NOW(), NOW()),
('明朝那些事儿·贰 万国来朝', '当年明月', '9787505722859', 3, 28.80, 2, '/uploads/e8c757a6d3754c56a523780ad3b11909.jpg', '从永乐大帝北伐蒙古、郑和下西洋，写到土木堡之变与北京保卫战。', NOW(), NOW()),
('明朝那些事儿·叁 妖孽宫廷', '当年明月', '9787505723269', 3, 28.80, 2, '/uploads/78d2a12076354fd8a18e0e396276ebf8.jpg', '从夺门之变写到王守仁平定宁王，见证紫禁城里的权谋与传奇。', NOW(), NOW()),
('明朝那些事儿·肆 粉饰太平', '当年明月', '9787505723788', 3, 28.80, 2, '/uploads/1686dfa5045e411bb75ae6f0b70d8c12.jpg', '从嘉靖议礼之争写到严嵩专权与东南抗倭。', NOW(), NOW()),
('明朝那些事儿·伍 帝国飘摇', '当年明月', '9787505724181', 3, 28.80, 2, '/uploads/c4b7bb194b2644e9a57dac40b086c997.jpg', '徐阶、高拱、张居正的内阁博弈，与万历援朝抗日战争。', NOW(), NOW()),
('明朝那些事儿·陆 日暮西山', '当年明月', '9787801655011', 3, 28.80, 2, '/uploads/58f99e9a16af466a8a951ef10d4d8cff.jpg', '三大案引发晚明党争，魏忠贤崛起与袁崇焕守辽东。', NOW(), NOW()),
('明朝那些事儿·柒 大结局', '当年明月', '9787801655998', 3, 29.80, 2, '/uploads/0734ad0afa0e4148bc77b40e5a36c320.jpg', '崇祯十七年：内忧外患之下，大明王朝的最终覆亡。', NOW(), NOW()),
('时间简史', '史蒂芬·霍金', '9787535794567', 4, 39.00, 5, '/uploads/da05e7782ba54ea0995f61dde97fdc2e.jpg', '探索宇宙起源与命运的科普经典。', NOW(), NOW()),
('经济学原理（第8版）', '曼昆', '9787301312971', 5, 109.00, 4, '/uploads/9c84e3b222d8402da2197eab2294cad8.jpg', '经济学入门经典教材。', NOW(), NOW());

-- 按各书目的 stock 自动生成副本
-- 条码规则：B + 书目id(3位) + 册序号(3位)，如 B001001
-- stock=0 的书目（如《三体》）不生成副本，用于演示预约排队
INSERT INTO `book_item` (`book_id`, `barcode`, `status`, `create_time`, `update_time`)
SELECT b.`id`,
       CONCAT('B', LPAD(b.`id`, 3, '0'), LPAD(n.`seq`, 3, '0')),
       0, NOW(), NOW()
FROM `book` b
JOIN (
  -- 生成 1~50 的序号，支撑单本书目最多 50 册的初始库存
  SELECT a.`n` + b2.`n` * 10 + 1 AS seq
  FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) b2
) n ON n.`seq` <= b.`stock`
WHERE b.`is_delete` = 0 AND b.`stock` > 0;

-- --------------------------------------------
-- 密码重置申请表
-- 读者忘记密码时提交申请，管理员电话核实后发放一次性重置码。
-- 管理员全程看不到用户的新密码，只负责"放行"。
-- --------------------------------------------
DROP TABLE IF EXISTS `password_reset_request`;
CREATE TABLE `password_reset_request` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT       NOT NULL COMMENT '匹配到的读者id',
  `account`       VARCHAR(32)  NOT NULL COMMENT '读者账号（冗余，便于列表展示）',
  `apply_name`    VARCHAR(50)  NOT NULL COMMENT '申请时填写的姓名',
  `apply_phone`   VARCHAR(20)  NOT NULL COMMENT '申请时填写的手机号',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1已发重置码 2已完成 3已拒绝/过期',
  `reset_code`    VARCHAR(6)   DEFAULT NULL COMMENT '一次性重置码',
  `code_expire`   DATETIME     DEFAULT NULL COMMENT '重置码过期时间',
  `handled_by`    BIGINT       DEFAULT NULL COMMENT '处理的管理员id',
  `handle_time`   DATETIME     DEFAULT NULL COMMENT '处理时间',
  `remark`        VARCHAR(255) DEFAULT NULL COMMENT '处理备注',
  `is_delete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time`   DATETIME     DEFAULT NULL COMMENT '申请时间',
  `update_time`   DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码重置申请';


-- --------------------------------------------
-- 3.0 新增：操作审计 / 站内信 / 对账 / ISBN 缓存
-- 与 patch-2026-11-04-v3.sql 等价，IF NOT EXISTS 保证可重复执行
-- --------------------------------------------
-- -------------------------------------------------------------
-- 操作审计日志
-- 资金流水只回答「钱怎么动的」，回答不了「谁点的按钮」。
-- 管理员扣赔偿、禁用账号、改副本状态都必须留痕。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT DEFAULT NULL COMMENT '操作人id',
  `user_account` VARCHAR(32) DEFAULT NULL COMMENT '操作人账号快照',
  `module` VARCHAR(32) DEFAULT NULL COMMENT '业务模块',
  `action` VARCHAR(64) DEFAULT NULL COMMENT '动作',
  `detail` VARCHAR(1000) DEFAULT NULL COMMENT '入参摘要JSON',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT '来源IP',
  `success` TINYINT DEFAULT 1 COMMENT '1成功 0失败',
  `error_msg` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_module_time` (`module`, `create_time`),
  KEY `idx_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- -------------------------------------------------------------
-- 站内信
-- dedupe_key 唯一索引是定时任务幂等的关键：
-- 没有它，同一笔借阅每小时都会被提醒一次。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '接收人',
  `type` INT NOT NULL COMMENT '1到期提醒 2逾期提醒 3预约到书 4系统',
  `title` VARCHAR(100) DEFAULT NULL COMMENT '标题',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '正文',
  `biz_id` BIGINT DEFAULT NULL COMMENT '关联业务id',
  `is_read` TINYINT DEFAULT 0 COMMENT '0未读 1已读',
  `dedupe_key` VARCHAR(64) DEFAULT NULL COMMENT '幂等键',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dedupe` (`dedupe_key`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信';

-- -------------------------------------------------------------
-- 对账差异
-- 只记录对不上的那几笔，全部一致时表是空的——这本身就是结论。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reconcile_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `batch_no` VARCHAR(32) NOT NULL COMMENT '批次号 REC+日期',
  `bill_date` DATE NOT NULL COMMENT '账期',
  `order_no` VARCHAR(64) DEFAULT NULL COMMENT '本地支付单号',
  `trade_no` VARCHAR(64) DEFAULT NULL COMMENT '渠道交易号',
  `borrow_id` BIGINT DEFAULT NULL COMMENT '关联借阅单id（退款滞留类差异用）',
  `local_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '本地金额',
  `channel_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '渠道金额',
  `diff_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '差额',
  `diff_type` INT NOT NULL COMMENT '1本地有渠道无 2渠道有本地无 3金额不符',
  `status` INT DEFAULT 0 COMMENT '0待处理 1已处理 2已忽略',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_no`),
  KEY `idx_status_time` (`status`, `create_time`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账差异记录';

-- -------------------------------------------------------------
-- ISBN 元数据缓存
-- 采购时一箱书连扫几十本，同一本可能扫多次；
-- 上游接口挂掉时，已查过的书仍能继续录入。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `isbn_cache` (
  `isbn` VARCHAR(20) NOT NULL COMMENT 'ISBN（去连字符）',
  `title` VARCHAR(200) DEFAULT NULL,
  `author` VARCHAR(200) DEFAULT NULL,
  `publisher` VARCHAR(200) DEFAULT NULL,
  `cover` VARCHAR(500) DEFAULT NULL,
  `raw` VARCHAR(1000) DEFAULT NULL COMMENT '原始补充信息',
  `create_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`isbn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ISBN元数据缓存';

-- 示例公告
INSERT INTO `notice` (`title`, `content`, `create_time`) VALUES
('欢迎使用书屿', '本平台支持免费借阅、押金担保、图书预约与直接购买，欢迎各位读者使用！', NOW()),
('借阅规则说明', '每位读者最多同时借阅 5 本，借期 30 天（从到馆核销起算）。借阅需缴纳与书价等额的押金，按期归还后全额退还；逾期按 1 元/天从押金中扣除，逾期满 30 天视为购买，押金不再退还。', NOW());
