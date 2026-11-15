-- =============================================================
-- 物流配送管理系统 · 数据结构回归测试
-- 前置：已执行 schema.sql 与 seed.sql
-- 用法：mysql -h127.0.0.1 -P3308 -uroot -pxxx < sql/test.sql
--
-- 覆盖场景：北京 → 上海（5 站去程）→ 收件人拒收 → 原路退回北京（4 站回程）；
--           另含"审批超时被拒后修改信息重提"的用例。
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
SET state                  = 'APPROVED',
    forward_station_count  = 5,
    current_seq            = 1,
    current_station_id     = @st_from,
    approve_by             = @admin,
    approve_time           = NOW(),
    version                = version + 1
WHERE id = @oid;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid), 'APPROVED', '03 批准后状态为已批准');
CALL assert_eq((SELECT forward_station_count FROM delivery_order WHERE id = @oid), '5', '04 去程站数记为 5');
CALL assert_eq((SELECT COUNT(*) FROM order_route WHERE order_id = @oid), '5', '05 去程路线写入 5 行');
CALL assert_eq((SELECT station_id FROM order_route WHERE order_id = @oid AND seq = 1), @st_from, '06 途经节点首行为始发站');
CALL assert_eq((SELECT station_id FROM order_route WHERE order_id = @oid AND seq = 5), @st_to, '07 途经节点末行为目的站');

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

-- ---------- 4. 拒收，生成回程 ----------
UPDATE order_route SET status = 'REFUSED' WHERE order_id = @oid AND seq = 5;

UPDATE delivery_order
SET state = 'RETURNING', refuse_reason = '收件人拒收', refuse_time = NOW()
WHERE id = @oid;

INSERT INTO order_route (order_id, seq, station_id, status)
SELECT @oid, t.seq, s.id, 'PENDING'
FROM (SELECT 6 AS seq, 'ST005' AS code
      UNION ALL SELECT 7, 'ST003'
      UNION ALL SELECT 8, 'ST002'
      UNION ALL SELECT 9, 'ST001') t
         JOIN station s ON s.code = t.code;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid), 'RETURNING', '13 拒收后进入退回中');
CALL assert_eq((SELECT COUNT(*) FROM order_route WHERE order_id = @oid), '9', '14 回程追加 4 行，共 9 行');
CALL assert_eq((SELECT station_id FROM order_route WHERE order_id = @oid AND seq = 6), (SELECT id FROM station WHERE code = 'ST005'), '15 回程首站为目的站的上一站');
CALL assert_eq((SELECT station_id FROM order_route WHERE order_id = @oid AND seq = 9), @st_from, '16 回程终点回到始发站');
CALL assert_eq((SELECT seq FROM order_route WHERE order_id = @oid ORDER BY seq DESC LIMIT 1), '9', '17 回程序号连续递增，无跳号');

-- 回程同样要求相邻站连通
CALL assert_eq((SELECT COUNT(*)
                FROM order_route r1
                         JOIN order_route r2 ON r2.order_id = r1.order_id AND r2.seq = r1.seq + 1
                         LEFT JOIN route_edge e
                                   ON e.station_a_id = LEAST(r1.station_id, r2.station_id)
                                       AND e.station_b_id = GREATEST(r1.station_id, r2.station_id)
                WHERE r1.order_id = @oid
                  AND r1.seq >= 5
                  AND e.id IS NULL), '0', '18 回程相邻站之间必须存在线路');

-- ---------- 5. 回到始发站，退回完成 ----------
UPDATE order_route
SET status = 'ARRIVED', actual_arrive_time = NOW()
WHERE order_id = @oid AND seq BETWEEN 6 AND 9;

UPDATE delivery_order
SET state = 'RETURNED', current_seq = 9, current_station_id = @st_from
WHERE id = @oid;

CALL assert_eq((SELECT state FROM delivery_order WHERE id = @oid), 'RETURNED', '19 回到始发站后状态为已退回');
CALL assert_eq((SELECT current_seq FROM delivery_order WHERE id = @oid), '9', '20 路线进度单调推进到 9，未回退');
CALL assert_eq((SELECT COUNT(*) FROM order_route WHERE order_id = @oid AND status = 'ARRIVED'), '7', '21 中间站到站记录 7 条（去程 3 + 回程 4），末段单独为 REFUSED');

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

-- ---------- 8. 级联删除 ----------
DELETE FROM delivery_order WHERE id = @oid;
CALL assert_eq((SELECT COUNT(*) FROM order_route WHERE order_id = @oid), '0', '32 删除订单时级联清理路线');

ROLLBACK;
DROP PROCEDURE IF EXISTS assert_eq;

SELECT '=== 32 条用例全部通过 ===' AS summary;
