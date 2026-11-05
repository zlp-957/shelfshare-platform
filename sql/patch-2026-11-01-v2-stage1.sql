-- ============================================
-- 共享图书借阅平台 2.0 改造脚本（阶段 1：表结构）
-- 日期：2026-11-01
-- 说明：在 library 库上执行，**只执行一次**
--       执行前已有备份：桌面/图书管理系统-1.0版本/library-db-backup-2026-11-01.sql
-- ============================================
USE `library`;

-- --------------------------------------------
-- 1. 新建 book_item：图书副本表
--    每册实体书一条记录，独立条码
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS `book_item` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `book_id`     BIGINT       NOT NULL COMMENT '所属书目id',
  `barcode`     VARCHAR(32)  NOT NULL COMMENT '副本条码（馆内唯一，贴在书内页）',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0在馆 1借出中 2已售出 3已买断 4遗失 5破损 6下架 7已预留',
  `location`    VARCHAR(64)  DEFAULT NULL COMMENT '馆内位置，如 A区-3排-02',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_delete`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME     DEFAULT NULL COMMENT '入库时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_barcode` (`barcode`),
  KEY `idx_book_status` (`book_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书副本表';

-- --------------------------------------------
-- 2. 建 pay_order：支付单表
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS `pay_order` (
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
-- 3. 新建 payment_flow：资金流水表（账本）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS `payment_flow` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT        NOT NULL COMMENT '用户id',
  `biz_type`      TINYINT       NOT NULL COMMENT '类型：1押金收入 2书款收入 3押金退还 4逾期扣款 5破损扣款 6买断扣款',
  `biz_id`        BIGINT        DEFAULT NULL COMMENT '关联业务id',
  `biz_no`        VARCHAR(32)   DEFAULT NULL COMMENT '关联单号',
  `direction`     TINYINT       NOT NULL COMMENT '方向：1收入 2支出',
  `amount`        DECIMAL(10,2) NOT NULL COMMENT '金额（正数）',
  `balance_after` DECIMAL(10,2) DEFAULT NULL COMMENT '预留，2.0 无余额，置 NULL',
  `remark`        VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `create_time`   DATETIME      DEFAULT NULL COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水表';

-- --------------------------------------------
-- 4. 新建 user_agreement：规则同意记录
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS `user_agreement` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT      NOT NULL COMMENT '用户id',
  `agreement_type` TINYINT     NOT NULL COMMENT '类型：1借阅规则 2购买须知 3用户协议',
  `version`        VARCHAR(16) NOT NULL COMMENT '协议版本号',
  `agreed_time`    DATETIME    NOT NULL COMMENT '同意时间',
  `client_ip`      VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type_version` (`user_id`, `agreement_type`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则同意记录';

-- --------------------------------------------
-- 5. 改造 borrow：增加副本、押金、核销字段
-- --------------------------------------------
ALTER TABLE `borrow`
  ADD COLUMN `item_id`         BIGINT        DEFAULT NULL COMMENT '副本id' AFTER `book_id`,
  ADD COLUMN `verify_time`     DATETIME      DEFAULT NULL COMMENT '借出核销时间（借期起点）' AFTER `borrow_time`,
  ADD COLUMN `deposit_amount`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '押金金额' AFTER `deadline_time`,
  ADD COLUMN `deduct_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已扣押金（逾期+破损）' AFTER `deposit_amount`,
  ADD COLUMN `refund_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已退押金' AFTER `deduct_amount`,
  ADD COLUMN `pay_order_no`    VARCHAR(32)   DEFAULT NULL COMMENT '押金支付单号' AFTER `refund_amount`,
  ADD COLUMN `update_time`     DATETIME      DEFAULT NULL COMMENT '更新时间',
  ADD KEY `idx_item` (`item_id`),
  ADD KEY `idx_pay_order` (`pay_order_no`);

-- --------------------------------------------
-- 6. 改造 book：stock 语义改为「在馆副本数」
-- --------------------------------------------
ALTER TABLE `book`
  MODIFY COLUMN `stock` INT NOT NULL DEFAULT 0 COMMENT '在馆副本数（派生值，由 book_item 统计维护）';

-- --------------------------------------------
-- 7. 改造 user：余额与惩罚期废弃
-- --------------------------------------------
ALTER TABLE `user`
  MODIFY COLUMN `balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '【已废弃】2.0 改为押金制',
  MODIFY COLUMN `punish_end_time` DATETIME DEFAULT NULL COMMENT '【已废弃】2.0 由押金制取代';

-- --------------------------------------------
-- 8. 数据迁移：为现有图书生成副本记录
--    条码规则：B + 书目id(3位) + 册序号(3位)，如 B001001
-- --------------------------------------------
INSERT INTO `book_item` (`book_id`, `barcode`, `status`, `create_time`, `update_time`)
SELECT b.`id`,
       CONCAT('B', LPAD(b.`id`, 3, '0'), LPAD(n.`seq`, 3, '0')),
       0, NOW(), NOW()
FROM `book` b
JOIN (
  SELECT 1 AS seq UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) n ON n.`seq` <= b.`stock`
WHERE b.`is_delete` = 0
  AND b.`stock` > 0;

-- --------------------------------------------
-- 9. 数据迁移：borrow.status 语义切换
--    旧：0借阅中 1已归还 2逾期转购买 3欠费待结算
--    新：0待取书 1借阅中 2已归还 3已买断 4已取消
--    先整体 +100 避免链式冲突，再按映射改回
-- --------------------------------------------
UPDATE `borrow` SET `status` = `status` + 100;

UPDATE `borrow` SET `status` = CASE `status`
    WHEN 100 THEN 1   -- 借阅中
    WHEN 101 THEN 2   -- 已归还
    WHEN 102 THEN 3   -- 逾期转购买 -> 已买断
    WHEN 103 THEN 3   -- 欠费待结算 -> 已买断
    ELSE `status` - 100
  END
WHERE `status` >= 100;

-- 历史借阅记录补上借期起点（用借阅时间代替）
UPDATE `borrow` SET `verify_time` = `borrow_time`
WHERE `verify_time` IS NULL AND `status` IN (1, 2, 3);

-- --------------------------------------------
-- 10. 校验
-- --------------------------------------------
SELECT '=== 新表 ===' AS info;
SHOW TABLES LIKE 'book\_item';
SHOW TABLES LIKE 'pay\_order';
SHOW TABLES LIKE 'payment\_flow';
SHOW TABLES LIKE 'user\_agreement';

SELECT '=== 副本生成结果 ===' AS info;
SELECT COUNT(*) AS 副本总数 FROM `book_item`;

SELECT '=== borrow 状态分布 ===' AS info;
SELECT `status`, COUNT(*) AS cnt FROM `borrow` GROUP BY `status` ORDER BY `status`;
