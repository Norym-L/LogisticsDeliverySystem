-- =============================================================
-- 轨迹地图坐标系升级：WGS84 → GCJ-02（高德底图）
--
-- 适用：已经执行过旧版 sql/alter_track_map.sql、station.latitude / longitude
--       里存的是 WGS84 的旧库；也适用于尚未加过经纬度列的库。
-- 可重复执行：列已存在时跳过 ADD COLUMN；坐标按 code 幂等 UPDATE；
--             列注释每次重设，结果一致。
--
-- 转换方法：公开的 eviltransform / coordtransform 标准 WGS84→GCJ-02 算法
--   Krasovsky 1940 椭球：a = 6378245.0，ee = 0.00669342162296594323。
--   对中国境内点：
--     dLat = transformLat(lng - 105, lat - 35)
--     dLng = transformLng(lng - 105, lat - 35)
--     radLat = lat * PI / 180
--     magic = 1 - ee * sin(radLat)^2
--     dLat = dLat * 180 / ((a * (1 - ee)) / (magic * sqrt(magic)) * PI)
--     dLng = dLng * 180 / (a / sqrt(magic) * cos(radLat) * PI)
--     GCJ-02 = (lat + dLat, lng + dLng)
--   输入为原 WGS84 坐标，输出四舍五入到 7 位小数（与 DECIMAL(10,7) 一致）。
--   转换在仓库外脚本执行，下面每行末尾注释保留原 WGS84 值，仅供对照。
--
-- 不修改 station.name / station.address；前端与接口统一使用 GCJ-02，
-- 与高德 JS API 2.0 底图对齐，不再做二次偏移转换。
-- =============================================================

USE logistics;

SET @db := DATABASE();

-- 1. 列不存在时补列（与 schema.sql 的 GCJ-02 注释一致）
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE station ADD COLUMN latitude DECIMAL(10, 7) NULL COMMENT ''GCJ-02 纬度（高德底图），轨迹地图用；不改变地址文本'' AFTER address',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'station' AND COLUMN_NAME = 'latitude'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE station ADD COLUMN longitude DECIMAL(10, 7) NULL COMMENT ''GCJ-02 经度（高德底图），与高德 JS API 一致'' AFTER latitude',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'station' AND COLUMN_NAME = 'longitude'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 修正列注释，避免旧库仍标着 WGS84 / OSM
ALTER TABLE station
    MODIFY COLUMN latitude DECIMAL(10, 7) NULL COMMENT 'GCJ-02 纬度（高德底图），轨迹地图用；不改变地址文本',
    MODIFY COLUMN longitude DECIMAL(10, 7) NULL COMMENT 'GCJ-02 经度（高德底图），与高德 JS API 一致';

-- 3. 写入 8 个转运中心的 GCJ-02 坐标（只改经纬度，不动站名与地址）
UPDATE station SET latitude = 39.9081515, longitude = 116.4906138 WHERE code = 'ST001'; -- 原 WGS84: 39.9068844, 116.4845489
UPDATE station SET latitude = 39.2089846, longitude = 117.1420007 WHERE code = 'ST002'; -- 原 WGS84: 39.2077916, 117.1355758
UPDATE station SET latitude = 36.6645849, longitude = 117.1517746 WHERE code = 'ST003'; -- 原 WGS84: 36.6641799, 117.1456723
UPDATE station SET latitude = 34.7210865, longitude = 113.7089361 WHERE code = 'ST004'; -- 原 WGS84: 34.7221614, 113.7027748
UPDATE station SET latitude = 31.8691090, longitude = 118.8294022 WHERE code = 'ST005'; -- 原 WGS84: 31.8711900, 118.8242900
UPDATE station SET latitude = 31.2047244, longitude = 121.2629090 WHERE code = 'ST006'; -- 原 WGS84: 31.2067567, 121.2584826
UPDATE station SET latitude = 30.6370476, longitude = 114.1821401 WHERE code = 'ST007'; -- 原 WGS84: 30.6394073, 114.1765693
UPDATE station SET latitude = 30.2791076, longitude = 120.0259611 WHERE code = 'ST008'; -- 原 WGS84: 30.2815002, 120.0212235

SELECT code, name, address, latitude, longitude
FROM station
WHERE code BETWEEN 'ST001' AND 'ST008'
ORDER BY code;

SELECT 'alter_track_map_gcj02.sql 执行完成：8 个转运中心已切换为 GCJ-02' AS summary;
