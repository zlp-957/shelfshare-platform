-- ============================================================
-- 测试报告修复补充脚本（可选执行）
-- 对应缺陷：D-16 订单号生成存在碰撞风险（表无唯一索引）
--
-- 说明：代码层已把订单号从"秒级时间 + 4 位随机数"改为
--       "PO + 毫秒级时间戳 + 3 位自增序列"，本脚本再加数据库唯一索引兜底。
-- 注意：若历史数据中已存在重复订单号，本语句会执行失败，
--       请先清理重复数据后再执行。
-- ============================================================

-- 1. 订单号唯一索引（存在重复数据时请先执行下面的排查语句）
-- SELECT order_no, COUNT(*) AS cnt FROM purchase_order
--  WHERE is_delete = 0 GROUP BY order_no HAVING cnt > 1;

ALTER TABLE purchase_order ADD UNIQUE INDEX uk_purchase_order_no (order_no);

-- 2. 图书逻辑删除后不再出现在查询中，为分类关联查询补充索引（性能优化，可选）
ALTER TABLE book ADD INDEX idx_book_type_id (type_id);

-- 3. 借阅记录按用户/图书/状态查询频繁，补充联合索引（性能优化，可选）
ALTER TABLE borrow ADD INDEX idx_borrow_user_status (user_id, status);
ALTER TABLE borrow ADD INDEX idx_borrow_book_status (book_id, status);
