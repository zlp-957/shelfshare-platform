-- ============================================================
-- 清理 2026-10-31 并发/预约回归测试产生的数据
--
-- 说明：图书库存已通过接口归还还原（book.id=1 → 4，book.id=8 → 2），
--       此处只需清理测试账号及其借阅/预约/订单记录。
-- 执行前请确认这 4 个账号确为测试账号。
-- ============================================================

-- 1. 清理关联业务记录
DELETE FROM borrow
 WHERE user_id IN (SELECT id FROM user WHERE account IN ('R20267771', 'R20267772', 'R20267773', 'R20267777'));

DELETE FROM book_reserve
 WHERE user_id IN (SELECT id FROM user WHERE account IN ('R20267771', 'R20267772', 'R20267773', 'R20267777'));

DELETE FROM purchase_order
 WHERE user_id IN (SELECT id FROM user WHERE account IN ('R20267771', 'R20267772', 'R20267773', 'R20267777'));

-- 2. 清理测试账号
DELETE FROM user
 WHERE account IN ('R20267771', 'R20267772', 'R20267773', 'R20267777');

-- 3. 核对：以下两条应返回 0
-- SELECT COUNT(*) FROM user WHERE account LIKE 'R202677__';
-- SELECT COUNT(*) FROM borrow WHERE user_id NOT IN (SELECT id FROM user);
