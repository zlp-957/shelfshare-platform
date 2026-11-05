-- =============================================================
-- 3.0 增量脚本：操作审计 / 站内信 / 对账 / ISBN 缓存
--
-- 用法：mysql -uroot -p <库名> < sql/patch-2026-11-04-v3.sql
--      例：mysql -uroot -p library      < sql/patch-2026-11-04-v3.sql
--          mysql -uroot -p library_test < sql/patch-2026-11-04-v3.sql
--
-- 脚本刻意不写 USE：库名由调用方指定，同一份脚本可同时用于主库与测试库。
-- 幂等：全部使用 CREATE TABLE IF NOT EXISTS，可重复执行
-- =============================================================

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
