-- =============================================================
-- 物流配送管理系统 · 基础数据（种子）
-- 执行顺序：先 schema.sql，再本文件
-- 依赖：schema.sql 已建库建表
--
-- 账号说明：全部初始密码 123456
--   password_hash = SHA-256(salt + 明文)，salt = MD5(username) 前 16 位
--   这样种子数据可复现，不用手工生成随机盐
-- =============================================================

USE logistics;

-- -------------------------------------------------------------
-- 1. 驿站：8 个，构成一张有环的网，保证任意两站之间存在多条候选路径
-- -------------------------------------------------------------
INSERT INTO station (code, name, address)
VALUES ('ST001', '北京转运中心', '北京市朝阳区京通大道 18 号'),
       ('ST002', '天津转运中心', '天津市北辰区京津公路 66 号'),
       ('ST003', '济南转运中心', '济南市历城区经十东路 1200 号'),
       ('ST004', '郑州转运中心', '郑州市管城回族区航海东路 9 号'),
       ('ST005', '南京转运中心', '南京市江宁区双龙大道 2100 号'),
       ('ST006', '上海转运中心', '上海市青浦区华徐公路 888 号'),
       ('ST007', '武汉转运中心', '武汉市东西湖区金山大道 76 号'),
       ('ST008', '杭州转运中心', '杭州市余杭区文一西路 969 号');


-- -------------------------------------------------------------
-- 2. 线路：10 条无向边
--    以代码配对再 join 出 id，避免依赖自增值。
--    里程取近似公路里程，作为最短路权重。
--
--    验证用：北京 → 上海
--      北京-天津-济南-南京-上海 = 137+320+620+300 = 1377（应被选中）
--      北京-郑州-武汉-南京-上海 = 690+520+540+300 = 2050
-- -------------------------------------------------------------
INSERT INTO route_edge (station_a_id, station_b_id, distance_km)
SELECT a.id, b.id, e.distance_km
FROM (SELECT 'ST001' AS a_code, 'ST002' AS b_code, 137.00 AS distance_km
      UNION ALL SELECT 'ST001', 'ST004', 690.00
      UNION ALL SELECT 'ST002', 'ST003', 320.00
      UNION ALL SELECT 'ST003', 'ST004', 440.00
      UNION ALL SELECT 'ST003', 'ST005', 620.00
      UNION ALL SELECT 'ST004', 'ST007', 520.00
      UNION ALL SELECT 'ST005', 'ST006', 300.00
      UNION ALL SELECT 'ST005', 'ST007', 540.00
      UNION ALL SELECT 'ST005', 'ST008', 280.00
      UNION ALL SELECT 'ST006', 'ST008', 175.00) e
         JOIN station a ON a.code = e.a_code
         JOIN station b ON b.code = e.b_code;


-- -------------------------------------------------------------
-- 3. 操作员：1 名路径管理员 + 每站 1 名节点管理员
-- -------------------------------------------------------------
INSERT INTO operator (username, password_hash, salt, role, station_id, real_name, phone)
SELECT 'admin', '1183405ccf247e302c7d7476c984889964635120c7bd964127115fe657afcb19',
       '21232f297a57a5a7', 'PATH_ADMIN', NULL, '张远程', '13800000001';

INSERT INTO operator (username, password_hash, salt, role, station_id, real_name, phone)
SELECT t.username, t.password_hash, t.salt, 'STATION_ADMIN', s.id, t.real_name, t.phone
FROM (SELECT 'st001' AS username, '5dacc871ac0cd4e78b4c41f8bc62d95e6d3030628f5f0a757a27135975b90e45' AS password_hash, 'b4e94cc021bdd46c' AS salt, 'ST001' AS code, '李京津' AS real_name, '13800000002' AS phone
      UNION ALL SELECT 'st002', '846859539f9d71aa6f20ba96577e418576329701837727b155df0efbca4b0473', '90d8766c98c96c20', 'ST002', '王海津', '13800000003'
      UNION ALL SELECT 'st003', '21c9d9b56e3f6b7f14071e1328d2ae6c734a5cde2835dfbd5d94b609fd66d218', '5a93de36c5fd3203', 'ST003', '赵泉城', '13800000004'
      UNION ALL SELECT 'st004', '495158aadde3343d94f64aec092b2a1e478cbdc95a33ea13e2588cedaae5a05f', 'a85ef5f1a7641f12', 'ST004', '陈中原', '13800000005'
      UNION ALL SELECT 'st005', '5b8305de927a69cc63a40509a48c7bd4210559ed02f98adbebcf7e54d49d93da', 'fcbf3a9197a035ed', 'ST005', '孙金陵', '13800000006'
      UNION ALL SELECT 'st006', '6acb8165c605e95b8c4b5ea5a495d21570ade04b64490aee177c8046e9073b8e', '2553214776b71b58', 'ST006', '周浦江', '13800000007'
      UNION ALL SELECT 'st007', '33c9ac44946c01afd1b4cb99108368762c5e76252c89b6d9f31491091382bec9', '7afb71f54b2cbfac', 'ST007', '吴江城', '13800000008'
      UNION ALL SELECT 'st008', 'a04b4dcdf12097c01e6c9331b5ab7c819dda27d827726534d4dcfacd593d15aa', 'baeee9b102338d82', 'ST008', '郑钱塘', '13800000009') t
         JOIN station s ON s.code = t.code;


-- -------------------------------------------------------------
-- 4. 配送员：每个驿站 2 名，共 16 名
--    最后一公里的指派要求"配送员属于目的站"，所以每个站都要有人，
--    否则发往该站的订单在末段会找不到合法承运人。
-- -------------------------------------------------------------
INSERT INTO postman (username, password_hash, salt, name, phone, station_id)
SELECT t.username, t.password_hash, t.salt, t.name, t.phone, s.id
FROM (SELECT 'pm001' AS username, 'da0fbf6942737e645526fdd6606b41952130b9e2123a6c5e3aaea404f7b04db8' AS password_hash, '87ef340506635912' AS salt, '何京生' AS name, '13900000001' AS phone, 'ST001' AS code
      UNION ALL SELECT 'pm002', '08dcd609c63525880e564ce945d9d5d0a7195cfc99ca04f4d7007a8567202b30', '9012507445bcc83c', '许朝阳', '13900000002', 'ST001'
      UNION ALL SELECT 'pm003', '39c47772b484876f6afb80b4973bea21ce4ae8021306118dd87dd500ee15ef8e', '726f24ea6b860399', '沈海河', '13900000003', 'ST002'
      UNION ALL SELECT 'pm004', 'db9a542a49887f8d51beec9c9e05e0deb0517e1b49d41c4cabb1b9463838d845', '1286d78dbf9936e8', '邵津门', '13900000004', 'ST002'
      UNION ALL SELECT 'pm005', '6d7f8092d69b21cfb1fd179e21679abe1001971425d688ae09bca394d916a27a', '116b84571fcca84e', '钱泉城', '13900000005', 'ST003'
      UNION ALL SELECT 'pm006', 'b1150900534507c82b7a517b55818d3de10f87a2a653ec03fbad72851ec62060', '3d1cc24befceba0d', '孟历下', '13900000006', 'ST003'
      UNION ALL SELECT 'pm007', '0ffdb75847eb1a347ee68b259c802d33f759c4300c58a5216986f41235aff14b', '00aae972bce12ae6', '陈中原', '13900000007', 'ST004'
      UNION ALL SELECT 'pm008', '2bcd73a311a374e4f5988b184fd41c2ce9ec7a80a124b22f61701f680894f0d6', '6de09bf87235258f', '岳商都', '13900000008', 'ST004'
      UNION ALL SELECT 'pm009', '4f79b84ab17a9b752269fb3b885a7822184908049aaa9c57619f267c7c8adb1e', 'b037d32b2c955f81', '孙金陵', '13900000009', 'ST005'
      UNION ALL SELECT 'pm010', 'c042d097aac568437a806ff248d768c3046a593c9ba3d914d4d658b8a57ff642', 'e28f23f0ea636cf0', '周秦淮', '13900000010', 'ST005'
      UNION ALL SELECT 'pm011', 'e071496ced6b25b279dab419a0e267bd56c87a8b1b1841fa99b51ff8963e19fa', '9434b4bd790b36fc', '吴申城', '13900000011', 'ST006'
      UNION ALL SELECT 'pm012', 'fbdd40b811e0f0a374400ecc6b8f85d7dd7fc292fa0e714583f13a6358133bfb', '155f62e5783a694e', '郑浦江', '13900000012', 'ST006'
      UNION ALL SELECT 'pm013', '59e2ed0c220f9f068ffb9d0003798a7dbf648bfc2c9674e861340f035f44f1b0', 'cca0859bbfa341b7', '冯江城', '13900000013', 'ST007'
      UNION ALL SELECT 'pm014', '994235c2e4ba78fa3a276738facc3c9fe2ca924300d94cc61efbc83c5587ac6e', '9e3b4932d9399e8c', '韩汉口', '13900000014', 'ST007'
      UNION ALL SELECT 'pm015', '1dd3b15be971ac8e62825aaaf67f22b30048b753f3e393a3a3d1abc56dbe4e2f', 'deb4406f000e2c93', '朱临安', '13900000015', 'ST008'
      UNION ALL SELECT 'pm016', '992cafe23289be1eb9b041f611fb1e4ecbe8e9e02774d114870397bf6665cbc2', 'e91d4afac625ca12', '曹钱塘', '13900000016', 'ST008') t
         JOIN station s ON s.code = t.code;


-- -------------------------------------------------------------
-- 自检：应返回 8 / 10 / 9 / 16
-- -------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM station)      AS station_cnt,
       (SELECT COUNT(*) FROM route_edge)   AS edge_cnt,
       (SELECT COUNT(*) FROM operator)     AS operator_cnt,
       (SELECT COUNT(*) FROM postman)      AS postman_cnt;
