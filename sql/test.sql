-- =============================================================
-- 物流配送管理系统 · 数据结构回归测试
-- 前置：已执行 schema.sql 与 seed.sql
-- 用法：mysql -h127.0.0.1 -P3308 -uroot -pxxx < sql/test.sql
--
-- 覆盖场景：北京 → 上海（5 站）→ 批准 → 始发站收件核价 → 逐站发运 →
--           妥投/拒收 → 生成方向相反的独立退回单；
--           另含"审批超时被拒后修改信息重提""首次发运前取消""手机号校验"等用例。
-- 全程在事务内执行，结束后 ROLLBACK，不留脏数据。
-- 断言失败会以 SQLSTATE 45000 中断并打印 [FAIL]，全通过打印 [PASS]。
-- =============================================================

USE logistics;

DROP PROCEDURE IF EXISTS assert_eq;

DELIMITER //
CREATE PROCEDURE assert_eq(IN actual_val VARCHAR(255), IN expected_val VARCHAR(255), IN msg VARCHAR(100))
BEGIN
    DECLARE m VARCHAR(255);
    IF actual_val <=> expected_val THEN
        SELECT CONCAT('[PASS] ', msg) AS result;
    ELSE
        SET m = CONCAT('[FAIL] ', msg, ' | 期望=', IFNULL(expected_val, 'NULL'), ' 实际=', IFNULL(actual_val, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = m;
    END IF;
END //
DELIMITER ;


START TRANSACTION;

SET @st_from = (SELECT id FROM station WHERE code = 'ST001');
SET @st_to   = (SELECT id FROM station WHERE code = 'ST006');
SET @admin   = (SELECT id FROM operator WHERE username = 'admin');
SET @pm      = (SELECT id FROM postman WHERE username = 'pm001');

-- ---------- 1. 下单 ----------
INSERT INTO delivery_order (order_no, sender_name, sender_phone, receiver_name, receiver_phone,
                            from_station_id, to_station_id, goods_desc, weight, state, approve_deadline)
VALUES ('LD20261115000001', '张三', '13700000001', '李四', '13700000002',
        @st_from, @st_to, '测试件', 1.25, 'WAITING', DATE_ADD(NOW(), INTERVAL 2 MINUTE));
SET @oid = LAST_INSERT_ID();

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid), 'WAITING', '01 下单后状态为待审批');
CALL assert_eq((SELECT current_seq FROM delivery_order WHERE id = @oid), NULL, '02 未批准时路线进度为空');

-- ---------- 2. 批准并固化去程路线 ----------
INSERT INTO order_route (order_id, seq, station_id, planned_time)
SELECT @oid, t.seq, s.id, DATE_ADD(NOW(), INTERVAL t.seq HOUR)
FROM (SELECT 1 AS seq, 'ST001' AS code
      UNION ALL SELECT 2, 'ST002'
      UNION ALL SELECT 3, 'ST003'
      UNION ALL SELECT 4, 'ST005'
      UNION ALL SELECT 5, 'ST006') t
         JOIN station s ON s.code = t.code;

UPDATE delivery_order
SET state                  = 'AWAITING_RECEIPT',
    forward_station_count  = 5,
    current_seq            = 1,
    current_station_id     = @st_from,
    approve_by             = @admin,
    approve_time           = NOW(),
    version                = version + 1
WHERE id = @oid;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid), 'AWAITING_RECEIPT', '03 批准后进入待始发收件');
CALL assert_eq((SELECT current_seq FROM delivery_order WHERE id = @oid), '1', '03b 批准后当前站为始发站');
CALL assert_eq((SELECT status FROM order_route WHERE order_id = @oid AND seq = 1), 'PENDING', '03c 批准后始发站路段尚未到站');
CALL assert_eq((SELECT forward_station_count FROM delivery_order WHERE id = @oid), '5', '04 去程站数记为 5');
CALL assert_eq((SELECT COUNT(*) FROM order_route WHERE order_id = @oid), '5', '05 去程路线写入 5 行');
CALL assert_eq((SELECT station_id FROM order_route WHERE order_id = @oid AND seq = 1), @st_from, '06 途经节点首行为始发站');
CALL assert_eq((SELECT station_id FROM order_route WHERE order_id = @oid AND seq = 5), @st_to, '07 途经节点末行为目的站');

-- 始发站确认收件并核价：这是全流程唯一一次最终核价入口
UPDATE delivery_order
SET state                    = 'APPROVED',
    receipt_confirmed_at     = NOW(),
    actual_volume_cm3        = 5000,
    actual_weight            = 1.25,
    price                    = 26,
    price_confirmed_at       = NOW(),
    price_confirmed_by       = @pm
WHERE id = @oid
  AND state = 'AWAITING_RECEIPT';

UPDATE order_route
SET status             = 'ARRIVED',
    actual_arrive_time = NOW()
WHERE order_id = @oid
  AND seq = 1
  AND status = 'PENDING';

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid), 'APPROVED', '07b 始发站收件后转待发运');
CALL assert_eq((SELECT price FROM delivery_order WHERE id = @oid), '26.00', '07c 收件时写入最终运费');
CALL assert_eq((SELECT status FROM order_route WHERE order_id = @oid AND seq = 1), 'ARRIVED', '07d 收件后始发站路段已到站');

-- 相邻两站必须在 route_edge 中存在边，否则路线不连通
CALL assert_eq((SELECT COUNT(*)
                FROM order_route r1
                         JOIN order_route r2 ON r2.order_id = r1.order_id AND r2.seq = r1.seq + 1
                         LEFT JOIN route_edge e
                                   ON e.station_a_id = LEAST(r1.station_id, r2.station_id)
                                       AND e.station_b_id = GREATEST(r1.station_id, r2.station_id)
                WHERE r1.order_id = @oid
                  AND r2.seq <= 5
                  AND e.id IS NULL), '0', '08 去程相邻站之间必须存在线路');
CALL assert_eq((SELECT CAST(SUM(e.distance_km) AS CHAR)
                FROM order_route r1
                         JOIN order_route r2 ON r2.order_id = r1.order_id AND r2.seq = r1.seq + 1
                         JOIN route_edge e
                              ON e.station_a_id = LEAST(r1.station_id, r2.station_id)
                                  AND e.station_b_id = GREATEST(r1.station_id, r2.station_id)
                WHERE r1.order_id = @oid
                  AND r2.seq <= 5), '1377.00', '09 去程总里程为最短路径 1377 km');

-- ---------- 3. 逐站推进到目的站 ----------
UPDATE order_route
SET actual_arrive_time = NOW(), status = 'ARRIVED'
WHERE order_id = @oid AND seq BETWEEN 2 AND 5;

UPDATE delivery_order
SET state = 'DELIVERING', current_seq = 5, current_station_id = @st_to
WHERE id = @oid;

UPDATE order_route SET status = 'DELIVERING', postman_id = @pm
WHERE order_id = @oid AND seq = 5;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid), 'DELIVERING', '10 到达目的站后状态为派送中');
CALL assert_eq((SELECT current_station_id FROM delivery_order WHERE id = @oid), @st_to, '11 当前位置快照为目的站');
CALL assert_eq((SELECT postman_id FROM order_route WHERE order_id = @oid AND seq = 5), @pm, '12 末段已分配配送员');

-- ---------- 4. 拒收：原单直接终结 ----------
UPDATE order_route SET status = 'REFUSED' WHERE order_id = @oid AND seq = 5;

UPDATE delivery_order
SET state = 'REFUSED', refuse_reason = '收件人拒收', refuse_time = NOW()
WHERE id = @oid;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid), 'REFUSED', '13 拒收后原单终结为已拒收');
CALL assert_eq((SELECT refuse_reason FROM delivery_order WHERE id = @oid), '收件人拒收', '14 拒收原因已记录');

-- ---------- 5. 生成退回单：方向相反，收发件人对调 ----------
-- 服务层做的事，这里用一条 INSERT ... SELECT 复现
INSERT INTO delivery_order (order_no, sender_name, sender_phone, receiver_name, receiver_phone,
                            from_station_id, to_station_id, goods_desc, weight,
                            state, approve_deadline, return_of)
SELECT 'LD20261115000009', receiver_name, receiver_phone, sender_name, sender_phone,
       to_station_id, from_station_id, goods_desc, weight,
       'WAITING', DATE_ADD(NOW(), INTERVAL 2 MINUTE), order_no
FROM delivery_order
WHERE id = @oid;
SET @rid = LAST_INSERT_ID();

CALL assert_eq((SELECT return_of FROM delivery_order WHERE id = @rid),
               (SELECT order_no FROM delivery_order WHERE id = @oid), '15 退回单记录了原单号');
CALL assert_eq((SELECT from_station_id FROM delivery_order WHERE id = @rid), @st_to, '16 退回单从原目的站发出');
CALL assert_eq((SELECT to_station_id FROM delivery_order WHERE id = @rid), @st_from, '17 退回单寄回原始发站');
CALL assert_eq((SELECT receiver_phone FROM delivery_order WHERE id = @rid), '13700000001', '18 退回单收件人是原寄件人');

-- 退回单走同一套流程：批准后生成自己的路线，站点逆序
UPDATE delivery_order
SET state = 'APPROVED', forward_station_count = 5, current_seq = 1, current_station_id = @st_to
WHERE id = @rid;

INSERT INTO order_route (order_id, seq, station_id, status)
SELECT @rid, t.seq, s.id, 'PENDING'
FROM (SELECT 1 AS seq, 'ST006' AS code
      UNION ALL SELECT 2, 'ST005'
      UNION ALL SELECT 3, 'ST003'
      UNION ALL SELECT 4, 'ST002'
      UNION ALL SELECT 5, 'ST001') t
         JOIN station s ON s.code = t.code;

CALL assert_eq((SELECT COUNT(*) FROM order_route WHERE order_id = @rid), '5', '19 退回单同样生成 5 行路线');
CALL assert_eq((SELECT station_id FROM order_route WHERE order_id = @rid AND seq = 5), @st_from, '20 退回单终点是原始发站');

UPDATE delivery_order
SET state = 'RECEIVED', receive_time = NOW(), current_seq = 5, current_station_id = @st_from
WHERE id = @rid;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @rid), 'RECEIVED', '21 退回单签收后终结');

-- ---------- 6. 被拒后修改重提（第二单） ----------
INSERT INTO delivery_order (order_no, sender_name, sender_phone, receiver_name, receiver_phone,
                            from_station_id, to_station_id, goods_desc, weight, state, approve_deadline)
VALUES ('LD20261115000002', '王五', '13700000003', '赵六', '13700000004',
        @st_from, @st_to, '重提测试件', 0.80, 'WAITING', DATE_ADD(NOW(), INTERVAL 2 MINUTE));
SET @oid2 = LAST_INSERT_ID();

UPDATE delivery_order SET state = 'REJECTED', reject_reason = '审批超时' WHERE id = @oid2;
CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid2), 'REJECTED', '22 超时后状态为已拒绝');

UPDATE delivery_order
SET state            = 'WAITING',
    receiver_phone   = '13700000009',
    reject_reason    = NULL,
    approve_deadline = DATE_ADD(NOW(), INTERVAL 2 MINUTE),
    resubmit_count   = resubmit_count + 1,
    version          = version + 1
WHERE id = @oid2;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid2), 'WAITING', '23 重提后回到待审批');
CALL assert_eq((SELECT resubmit_count FROM delivery_order WHERE id = @oid2), '1', '24 重提次数累加为 1');
CALL assert_eq((SELECT reject_reason FROM delivery_order WHERE id = @oid2), NULL, '25 重提时清空上次拒绝原因');
CALL assert_eq((SELECT receiver_phone FROM delivery_order WHERE id = @oid2), '13700000009', '26 修改后的收寄信息已生效');
CALL assert_eq((SELECT IF(approve_deadline > NOW(), '1', '0') FROM delivery_order WHERE id = @oid2), '1', '27 审批截止时间已重置');

-- ---------- 7. 正常签收（第三单） ----------
INSERT INTO delivery_order (order_no, sender_name, sender_phone, receiver_name, receiver_phone,
                            from_station_id, to_station_id, goods_desc, weight, state, approve_deadline)
VALUES ('LD20261115000003', '孙七', '13700000005', '周八', '13700000006',
        @st_from, @st_to, '签收测试件', 0.30, 'WAITING', DATE_ADD(NOW(), INTERVAL 2 MINUTE));
SET @oid3 = LAST_INSERT_ID();

INSERT INTO order_route (order_id, seq, station_id, planned_time)
SELECT @oid3, t.seq, s.id, DATE_ADD(NOW(), INTERVAL t.seq HOUR)
FROM (SELECT 1 AS seq, 'ST001' AS code
      UNION ALL SELECT 2, 'ST002'
      UNION ALL SELECT 3, 'ST003'
      UNION ALL SELECT 4, 'ST005'
      UNION ALL SELECT 5, 'ST006') t
         JOIN station s ON s.code = t.code;

UPDATE delivery_order
SET state = 'APPROVED', forward_station_count = 5, current_seq = 1, current_station_id = @st_from
WHERE id = @oid3;
UPDATE order_route SET actual_arrive_time = NOW(), status = 'ARRIVED'
WHERE order_id = @oid3 AND seq BETWEEN 2 AND 5;
UPDATE delivery_order SET state = 'DELIVERING', current_seq = 5, current_station_id = @st_to
WHERE id = @oid3;

-- 手机号不匹配时，条件更新应影响 0 行
UPDATE delivery_order SET state = 'RECEIVED', receive_time = NOW()
WHERE id = @oid3 AND state = 'DELIVERING' AND receiver_phone = '00000000000';
CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid3), 'DELIVERING', '28 手机号不匹配时签收不生效');

-- 手机号匹配则签收成功
UPDATE delivery_order SET state = 'RECEIVED', receive_time = NOW()
WHERE id = @oid3 AND state = 'DELIVERING' AND receiver_phone = '13700000006';
UPDATE order_route SET status = 'SIGNED' WHERE order_id = @oid3 AND seq = 5;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid3), 'RECEIVED', '29 手机号匹配时签收成功');
CALL assert_eq((SELECT IF(receive_time IS NULL, '0', '1') FROM delivery_order WHERE id = @oid3), '1', '30 签收时间已记录');
CALL assert_eq((SELECT status FROM order_route WHERE order_id = @oid3 AND seq = 5), 'SIGNED', '31 末段路线标记为已签收');

-- ---------- 8. 取消：仅首次发运前 ----------
INSERT INTO delivery_order (order_no, sender_name, sender_phone, receiver_name, receiver_phone,
                            from_station_id, to_station_id, goods_desc, weight, state, approve_deadline)
VALUES ('LD20261115000004', '钱九', '13700000011', '吴十', '13700000012',
        @st_from, @st_to, '取消测试件', 0.50, 'WAITING', DATE_ADD(NOW(), INTERVAL 2 MINUTE));
SET @oid4 = LAST_INSERT_ID();

UPDATE delivery_order
SET state = 'CANCELLED', cancel_time = NOW()
WHERE id = @oid4
  AND sender_phone = '13700000011'
  AND state IN ('WAITING', 'AWAITING_RECEIPT', 'APPROVED')
  AND NOT EXISTS (SELECT 1 FROM order_route r WHERE r.order_id = @oid4 AND r.depart_time IS NOT NULL);
CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid4), 'CANCELLED', '33 待审批订单可由寄件人取消');

-- 手机号不匹配时取消不生效
INSERT INTO delivery_order (order_no, sender_name, sender_phone, receiver_name, receiver_phone,
                            from_station_id, to_station_id, goods_desc, weight, state, approve_deadline)
VALUES ('LD20261115000005', '钱九', '13700000011', '吴十', '13700000012',
        @st_from, @st_to, '取消校验件', 0.50, 'WAITING', DATE_ADD(NOW(), INTERVAL 2 MINUTE));
SET @oid5 = LAST_INSERT_ID();

UPDATE delivery_order
SET state = 'CANCELLED', cancel_time = NOW()
WHERE id = @oid5
  AND sender_phone = '13700000099'
  AND state IN ('WAITING', 'AWAITING_RECEIPT', 'APPROVED');
CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid5), 'WAITING', '34 手机号不匹配时取消不生效');

-- 已批准但尚未发运（待始发收件）仍可取消
UPDATE delivery_order SET state = 'AWAITING_RECEIPT' WHERE id = @oid5;
UPDATE delivery_order
SET state = 'CANCELLED', cancel_time = NOW()
WHERE id = @oid5
  AND sender_phone = '13700000011'
  AND state IN ('WAITING', 'AWAITING_RECEIPT', 'APPROVED')
  AND NOT EXISTS (SELECT 1 FROM order_route r WHERE r.order_id = @oid5 AND r.depart_time IS NOT NULL);
CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid5), 'CANCELLED', '35 待始发收件订单仍可取消');

-- 首次发运后不可取消：NOT EXISTS 是状态机之外的最后一道保险
INSERT INTO delivery_order (order_no, sender_name, sender_phone, receiver_name, receiver_phone,
                            from_station_id, to_station_id, goods_desc, weight, state, approve_deadline)
VALUES ('LD20261115000006', '钱九', '13700000011', '吴十', '13700000012',
        @st_from, @st_to, '已发运件', 0.50, 'APPROVED', DATE_ADD(NOW(), INTERVAL 2 MINUTE));
SET @oid6 = LAST_INSERT_ID();
INSERT INTO order_route (order_id, seq, station_id, depart_time, status)
VALUES (@oid6, 1, @st_from, NOW(), 'ARRIVED');

UPDATE delivery_order
SET state = 'CANCELLED', cancel_time = NOW()
WHERE id = @oid6
  AND sender_phone = '13700000011'
  AND state IN ('WAITING', 'AWAITING_RECEIPT', 'APPROVED')
  AND NOT EXISTS (SELECT 1 FROM order_route r WHERE r.order_id = @oid6 AND r.depart_time IS NOT NULL);
CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid6), 'APPROVED', '36 已首次发运的订单不可取消');

-- ---------- 9. 级联删除 ----------
DELETE FROM delivery_order WHERE id = @oid;
CALL assert_eq((SELECT COUNT(*) FROM order_route WHERE order_id = @oid), '0', '32 删除订单时级联清理路线');

ROLLBACK;
DROP PROCEDURE IF EXISTS assert_eq;

SELECT '=== 41 条用例全部通过 ===' AS summary;
