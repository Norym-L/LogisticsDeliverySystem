-- =============================================================
-- 物流配送管理系统 · 数据库结构（DDL）
-- 目标库：MySQL 8.0.16+（CHECK 约束自 8.0.16 起才强制生效）
-- 引擎：InnoDB    字符集：utf8mb4
--
-- 设计要点（答辩可讲）：
--   1. 寄件人/收件人不建账号表。设计里用户只凭手机号查单、凭单号查详情，
--      没有登录动作，单独建 user 表只会多出一堆没人用的注册/改密接口。
--   2. 驿站是静态字典，订单的到站时间是业务事实，分表存放。二者混在一起
--      会导致同一个驿站在不同订单上有不同时间戳，无法表达。
--   3. 计划路线与实际到站合并为 order_route 一张表：业务上实际路线不得
--      偏离计划路线，一行同时记录"计划何时到"和"实际何时到"。
--   4. 车次属于某一段运输而非整单，故落在 order_route.arrive_trip_no。
--      若放在订单表，后一个中转站分配的车次会覆盖前一段的记录。
--   5. 拒收原路退回：回程不另建表也不另建序列，直接接在 order_route 的
--      seq 之后（去程 1..n，回程 n+1..2n-1，站点逆序）。于是"取下一节点"
--      永远是 seq = current_seq + 1，去程与回程复用同一套推进逻辑。
-- =============================================================

CREATE DATABASE IF NOT EXISTS logistics
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE logistics;

-- -------------------------------------------------------------
-- 警告：以下 DROP 会清空全部数据，仅用于开发期重建。
-- 首次部署或交付时请注释掉这一段。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS order_route;
DROP TABLE IF EXISTS delivery_order;
DROP TABLE IF EXISTS postman;
DROP TABLE IF EXISTS operator;
DROP TABLE IF EXISTS route_edge;
DROP TABLE IF EXISTS station;


-- -------------------------------------------------------------
-- 1. station 驿站字典
-- -------------------------------------------------------------
CREATE TABLE station
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(16)     NOT NULL COMMENT '业务编号，如 ST001',
    name        VARCHAR(64)     NOT NULL COMMENT '驿站名称',
    address     VARCHAR(255)    NOT NULL COMMENT '地址',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_station_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='驿站字典';


-- -------------------------------------------------------------
-- 2. route_edge 线路（无向图的一条边）
--    约定 station_a_id < station_b_id，配合唯一键杜绝 (1,2)/(2,1)
--    同时存在。否则最短路算法会读到两条重复边，距离比较必然出错。
--    运行期一次性全表加载进内存跑 Dijkstra，图规模在几十条边以内。
-- -------------------------------------------------------------
CREATE TABLE route_edge
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    station_a_id BIGINT UNSIGNED NOT NULL COMMENT '较小的一端',
    station_b_id BIGINT UNSIGNED NOT NULL COMMENT '较大的一端',
    distance_km  DECIMAL(6, 2)  NOT NULL COMMENT '里程，最短路权重',
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1 可用 / 0 停用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edge (station_a_id, station_b_id),
    KEY idx_edge_b (station_b_id),
    CONSTRAINT ck_edge_order CHECK (station_a_id < station_b_id),
    CONSTRAINT fk_edge_a FOREIGN KEY (station_a_id) REFERENCES station (id),
    CONSTRAINT fk_edge_b FOREIGN KEY (station_b_id) REFERENCES station (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='驿站间线路（无向图）';


-- -------------------------------------------------------------
-- 3. operator 操作员（两类角色）
--    PATH_ADMIN    路径管理员：检查请求、批准、选择途经节点，station_id 为空
--    STATION_ADMIN 节点管理员：取下一节点、分配车次与派送员，station_id 必填
--    密码为 SHA-256(salt + 明文) 的十六进制串。生产环境应改 BCrypt。
-- -------------------------------------------------------------
CREATE TABLE operator
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(32)     NOT NULL COMMENT '登录名',
    password_hash CHAR(64)        NOT NULL COMMENT 'SHA-256(salt + 明文) 十六进制',
    salt          CHAR(16)        NOT NULL COMMENT '随机盐',
    role          VARCHAR(16)     NOT NULL COMMENT 'PATH_ADMIN / STATION_ADMIN',
    station_id    BIGINT UNSIGNED NULL COMMENT '所属驿站；路径管理员为空',
    real_name     VARCHAR(32)     NOT NULL COMMENT '姓名',
    phone         VARCHAR(20)     NOT NULL COMMENT '联系电话',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_operator_username (username),
    KEY idx_operator_station (station_id),
    CONSTRAINT ck_operator_role CHECK (role IN ('PATH_ADMIN', 'STATION_ADMIN')),
    CONSTRAINT fk_operator_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='操作员（路径管理员 / 节点管理员）';


-- -------------------------------------------------------------
-- 4. postman 配送员
--    独立于 operator：它是被分配的对象，业务档案与操作员不同。
--    但同样需要登录，凭据放在本表（两个登录入口，见接口设计）。
-- -------------------------------------------------------------
CREATE TABLE postman
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(32)     NOT NULL COMMENT '登录名',
    password_hash CHAR(64)        NOT NULL COMMENT 'SHA-256(salt + 明文) 十六进制',
    salt          CHAR(16)        NOT NULL COMMENT '随机盐',
    name          VARCHAR(32)     NOT NULL COMMENT '姓名',
    phone         VARCHAR(20)     NOT NULL COMMENT '联系电话',
    station_id    BIGINT UNSIGNED NOT NULL COMMENT '所属末端驿站',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 在岗 / 0 离岗',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_postman_username (username),
    KEY idx_postman_station (station_id),
    CONSTRAINT fk_postman_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='配送员';


-- -------------------------------------------------------------
-- 5. delivery_order 订单
--    表名不叫 order：ORDER 是 MySQL 保留字，建表能过，但此后每条 SQL、
--    每个 MyBatis 动态标签都要反引号，漏一个就是运行时错误。
--
--    state 取值：
--      WAITING    待审批（发起后进入，等待路径管理员处理）
--      APPROVED   已批准（路线已固化，等待起点站发运）
--      REJECTED   已拒绝（管理员拒绝或审批超时）。不是终态：
--                 用户可修改收寄信息后重提，状态回到 WAITING，
--                 重提次数记在 resubmit_count，业务层据此设上限。
--      SENDING    运送中（去程）
--      DELIVERING 派送中（已到目的站并分配配送员）
--      RECEIVED   已签收（终态）
--      RETURNING  退回中（收件人拒收，按原路返回始发站）
--      RETURNED   已退回（终态）
--
--    没有 REFUSED 状态：拒收是"派送动作的结果"，业务后果是进入回程，
--    因此状态直接转为 RETURNING。拒收事实记在 refuse_reason / refuse_time
--    以及 order_route 末段的 status = 'REFUSED' 上。
--
--    current_seq / current_station_id 两字段必须同一事务更新：
--    前者是唯一真相，后者是冗余快照，用于"本站待处理货物"这类高频查询。
--
--    version 乐观锁：审批接口与超时任务可能同时打到同一单，
--    靠 update ... where id=? and state='WAITING' 收敛。
-- -------------------------------------------------------------
CREATE TABLE delivery_order
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no         VARCHAR(32)     NOT NULL COMMENT '业务单号，LD + yyyyMMdd + UUID 前 12 位，共 22 字符',
    sender_name      VARCHAR(32)     NOT NULL COMMENT '寄件人姓名',
    sender_phone     VARCHAR(20)     NOT NULL COMMENT '寄件人手机号',
    receiver_name    VARCHAR(32)     NOT NULL COMMENT '收件人姓名',
    receiver_phone   VARCHAR(20)     NOT NULL COMMENT '收件人手机号',
    from_station_id  BIGINT UNSIGNED NOT NULL COMMENT '始发驿站',
    to_station_id    BIGINT UNSIGNED NOT NULL COMMENT '目的驿站',
    goods_desc       VARCHAR(255)    NULL COMMENT '货物描述',
    weight           DECIMAL(8, 2)   NULL COMMENT '重量 kg',
    state            VARCHAR(16)     NOT NULL COMMENT '订单状态',
    current_seq      INT             NULL COMMENT '已推进到 order_route 的第几站',
    current_station_id BIGINT UNSIGNED NULL COMMENT '当前所在驿站（冗余快照）',
    approve_deadline DATETIME        NOT NULL COMMENT '审批截止时间，超时自动拒绝；每次重提重置',
    approve_by       BIGINT UNSIGNED NULL COMMENT '批准人',
    approve_time     DATETIME        NULL COMMENT '批准时间',
    reject_reason    VARCHAR(255)    NULL COMMENT '审批拒绝原因，重提时清空',
    refuse_reason    VARCHAR(255)    NULL COMMENT '收件人拒收原因',
    refuse_time      DATETIME        NULL COMMENT '拒收时间',
    receive_time     DATETIME        NULL COMMENT '签收时间',
    forward_station_count INT        NULL COMMENT '去程站数；seq<=此值为去程，>为回程',
    resubmit_count   INT             NOT NULL DEFAULT 0 COMMENT '被拒后修改重提的次数，业务层限制上限',
    version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_sender_phone (sender_phone),
    KEY idx_receiver_phone (receiver_phone),
    KEY idx_state_deadline (state, approve_deadline),
    KEY idx_current_station (current_station_id, state),
    CONSTRAINT ck_order_state CHECK (state IN ('WAITING', 'APPROVED', 'REJECTED', 'SENDING',
                                               'DELIVERING', 'RECEIVED', 'RETURNING', 'RETURNED')),
    CONSTRAINT fk_order_from FOREIGN KEY (from_station_id) REFERENCES station (id),
    CONSTRAINT fk_order_to FOREIGN KEY (to_station_id) REFERENCES station (id),
    CONSTRAINT fk_order_approve_by FOREIGN KEY (approve_by) REFERENCES operator (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='配送订单';


-- -------------------------------------------------------------
-- 6. order_route 订单路线（计划 + 实际）
--    途经节点必须包含始发站和目的站：
--      去程 seq 1 = 始发站，seq n = 目的站，中间为途经站。
--    批准时由路径管理员确定（系统给最短路径建议，允许调整，但必须连通），
--    确认后整条路线固化写入，实际运输不得偏离。
--
--    拒收原路退回：回程接在同一序列之后，站点逆序，
--      seq n+1 = 目的站的上一站 … seq 2n-1 = 始发站。
--      一张表同时承载去程与回程，current_seq 单调递增，
--      "取下一节点" 永远是 seq = current_seq + 1，两段共用同一套代码。
--
--    arrive_trip_no：抵达本站所乘车次，始发站为空。
--    postman_id    ：仅去程末段（seq = forward_station_count）使用。
--    status        ：PENDING 未到 / ARRIVED 已到站 / DELIVERING 派送中 /
--                    SIGNED 已签收 / REFUSED 已拒收
--                    后三种只出现在去程末段。
-- -------------------------------------------------------------
CREATE TABLE order_route
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id           BIGINT UNSIGNED NOT NULL COMMENT '订单',
    seq                INT             NOT NULL COMMENT '顺序，1 为始发站',
    station_id         BIGINT UNSIGNED NOT NULL COMMENT '驿站',
    arrive_trip_no     VARCHAR(32)     NULL COMMENT '抵达本站所乘车次，始发站为空',
    planned_time       DATETIME        NULL COMMENT '预计到达时间',
    actual_arrive_time DATETIME        NULL COMMENT '实际到达时间',
    arrive_operator_id BIGINT UNSIGNED NULL COMMENT '确认到站的操作员',
    postman_id         BIGINT UNSIGNED NULL COMMENT '去程末段的派送员',
    status             VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '路段状态',
    remark             VARCHAR(255)    NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_order_seq (order_id, seq),
    KEY idx_route_station (station_id, status),
    KEY idx_route_postman (postman_id, status),
    CONSTRAINT ck_route_seq CHECK (seq >= 1),
    CONSTRAINT ck_route_status CHECK (status IN ('PENDING', 'ARRIVED', 'DELIVERING', 'SIGNED', 'REFUSED')),
    CONSTRAINT fk_route_order FOREIGN KEY (order_id) REFERENCES delivery_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_route_station FOREIGN KEY (station_id) REFERENCES station (id),
    CONSTRAINT fk_route_operator FOREIGN KEY (arrive_operator_id) REFERENCES operator (id),
    CONSTRAINT fk_route_postman FOREIGN KEY (postman_id) REFERENCES postman (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单路线（计划与实际合一）';
