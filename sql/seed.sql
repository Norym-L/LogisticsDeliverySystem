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
--
--    经纬度坐标系：GCJ-02（高德坐标系），与高德 JS API 2.0 底图一致；
--    接口原样返回，前端不再把坐标假设为 WGS84，也不做二次偏移转换。
--
--    转换方法：用公开的 eviltransform / coordtransform 标准 WGS84→GCJ-02 算法
--    （Krasovsky 1940 椭球，a=6378245.0，ee=0.00669342162296594323；
--      对中国境内点先按经纬度多项式求偏移量，再按椭球曲率换算成度），
--    输入原 WGS84 坐标，输出四舍五入到 7 位小数。每行末尾注释保留原 WGS84 值，
--    仅供对照，不要拿它再写回数据库。站名和 address 文本均未改动。
--
--    原始 WGS84 坐标选取依据（2026-11 通过 OSM Nominatim / 公开地址资料核对）：
--      ST001 北京：地址中的"京通大道"是朝阳门—通州一带的历史道路名，现为京通快速路；
--                  取京通快速路四惠桥段 39.9068844, 116.4845489，道路级近似，非门牌级。
--      ST002 天津：京津公路天穆镇段 39.2077916, 117.1355758；公开地图没有 66 号门牌，
--                  按北辰区南段门牌规律取道路级近似。
--      ST003 济南：经十东路舜华路（历城区/高新区）段 36.6641799, 117.1456723；
--                  1200 号门牌无法单独定位，按历城区段道路级近似。
--      ST004 郑州：航海东路与中州大道交叉口金色港湾公交站 34.7221614, 113.7027748；
--                  公开地址资料显示航海东路 9 号院就在该路口附近。
--      ST005 南京：双龙大道与秣周东路交叉口 31.8711900, 118.8242900；
--                  2100 号位于双龙大道南段（九龙湖/上秦淮方向），道路级近似。
--      ST006 上海：华徐公路与北青公路交叉口 31.2067567, 121.2584826；
--                  公开地址资料显示华徐公路 888 号（中国梦谷）在该路口。
--      ST007 武汉：金山大道金银湖段 30.6394073, 114.1765693；76 号属东端小号段，
--                  道路级近似。
--      ST008 杭州：文一西路 969 号即阿里巴巴西溪 A 区，
--                  OSM 地址点 30.2815002, 120.0212235，门牌级精确。
--
--    因此除 ST008 外，其余坐标是"道路/路口级"而非屋顶级；只用于画折线和定位所在站。
-- -------------------------------------------------------------
INSERT INTO station (code, name, address, latitude, longitude)
VALUES ('ST001', '北京转运中心', '北京市朝阳区京通大道 18 号', 39.9081515, 116.4906138), -- 原 WGS84: 39.9068844, 116.4845489
       ('ST002', '天津转运中心', '天津市北辰区京津公路 66 号', 39.2089846, 117.1420007), -- 原 WGS84: 39.2077916, 117.1355758
       ('ST003', '济南转运中心', '济南市历城区经十东路 1200 号', 36.6645849, 117.1517746), -- 原 WGS84: 36.6641799, 117.1456723
       ('ST004', '郑州转运中心', '郑州市管城回族区航海东路 9 号', 34.7210865, 113.7089361), -- 原 WGS84: 34.7221614, 113.7027748
       ('ST005', '南京转运中心', '南京市江宁区双龙大道 2100 号', 31.8691090, 118.8294022), -- 原 WGS84: 31.8711900, 118.8242900
       ('ST006', '上海转运中心', '上海市青浦区华徐公路 888 号', 31.2047244, 121.2629090), -- 原 WGS84: 31.2067567, 121.2584826
       ('ST007', '武汉转运中心', '武汉市东西湖区金山大道 76 号', 30.6370476, 114.1821401), -- 原 WGS84: 30.6394073, 114.1765693
       ('ST008', '杭州转运中心', '杭州市余杭区文一西路 969 号', 30.2791076, 120.0259611); -- 原 WGS84: 30.2815002, 120.0212235


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
