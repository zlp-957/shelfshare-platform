-- =============================================================
-- 3.1 增量脚本：退款滞留计时精确化 + 对账差异关联借阅单
--
-- 用法：mysql -uroot -p <库名> < sql/patch-2026-11-04-v4.sql
--      例：mysql -uroot -p library      < sql/patch-2026-11-04-v4.sql
--          mysql -uroot -p library_test < sql/patch-2026-11-04-v4.sql
--
-- 脚本刻意不写 USE：库名由调用方指定，同一份脚本能直接跑主库和测试库，
-- 不会出现「对测试库执行却改错库」这种事故。下面的 information_schema
-- 判断用的是 DATABASE()，即当前连接的库。
--
-- 背景：上一版「退款滞留」检测用 update_time 当计时起点、按 order_no 去重，
-- 两个隐患：
--   1) update_time 会被任何无关更新推后，滞留该报的时间被延迟；
--   2) 合并支付单下多笔借阅同时滞留时只报一条，另一笔被掩盖。
-- 本脚本补两个字段解决，可重复执行。
-- =============================================================

-- -------------------------------------------------------------
-- borrow.refund_fail_time：退款失败时刻（滞留计时起点）
-- 只在「标记退款失败」时写入，之后不再被其他更新触碰
-- -------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'borrow'
    AND COLUMN_NAME = 'refund_fail_time'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `borrow` ADD COLUMN `refund_fail_time` DATETIME DEFAULT NULL COMMENT ''退款失败时刻（滞留计时起点）'' AFTER `refund_status`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- reconcile_record.borrow_id：差异关联的借阅单
-- 退款滞留按 borrow_id 精确去重，合并单下每笔都能单独报出来
-- -------------------------------------------------------------
SET @col_exists2 := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'reconcile_record'
    AND COLUMN_NAME = 'borrow_id'
);
SET @ddl2 := IF(@col_exists2 = 0,
  'ALTER TABLE `reconcile_record` ADD COLUMN `borrow_id` BIGINT DEFAULT NULL COMMENT ''关联借阅单id（退款滞留类差异用）'' AFTER `trade_no`',
  'SELECT 1');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 已存在的历史滞留记录：回填 borrow_id（从备注里的 borrowId= 提取）
UPDATE `reconcile_record`
SET `borrow_id` = CAST(
      SUBSTRING_INDEX(SUBSTRING_INDEX(`remark`, 'borrowId=', -1), ')', 1) AS UNSIGNED)
WHERE `diff_type` = 4
  AND `borrow_id` IS NULL
  AND `remark` LIKE '%borrowId=%';
