-- 为已有物流库增加“轨迹地图”所需的驿站 WGS84 经纬度，不删除数据。
-- 前置：已经执行过 schema.sql 及后续增量脚本。
-- 可重复执行：列已存在时跳过 ADD COLUMN；坐标按 code 幂等 UPDATE。
--
-- 坐标系：WGS84（EPSG:4326），与 Leaflet + OpenStreetMap 底图一致。
-- 不修改 station.name / station.address，也不做 GCJ-02 / BD-09 转换。
--
-- 坐标选取依据（2026-11 通过 OSM Nominatim / 公开地址资料核对）：
--   ST001 北京：地址中的“京通大道”是朝阳门—通州一带的历史道路名，现为京通快速路；
--               取京通快速路四惠桥段 39.9068844, 116.4845489，道路级近似，非门牌级。
--   ST002 天津：京津公路天穆镇段 39.2077916, 117.1355758；公开地图没有 66 号门牌，
--               按北辰区南段门牌规律取道路级近似。
--   ST003 济南：经十东路舜华路（历城区/高新区）段 36.6641799, 117.1456723；
--               1200 号门牌无法单独定位，按历城区段道路级近似。
--   ST004 郑州：航海东路与中州大道交叉口金色港湾公交站 34.7221614, 113.7027748；
--               公开地址资料显示航海东路 9 号院就在该路口附近。
--   ST005 南京：双龙大道与秣周东路交叉口 31.8711900, 118.8242900；
--               2100 号位于双龙大道南段（九龙湖/上秦淮方向），道路级近似。
--   ST006 上海：华徐公路与北青公路交叉口 31.2067567, 121.2584826；
--               公开地址资料显示华徐公路 888 号（中国梦谷）在该路口。
--   ST007 武汉：金山大道金银湖段 30.6394073, 114.1765693；76 号属东端小号段，
--               道路级近似。
--   ST008 杭州：文一西路 969 号即阿里巴巴西溪 A 区，
--               OSM 地址点 30.2815002, 120.0212235，门牌级精确。

USE logistics;

SET @db := DATABASE();

-- 1. 纬度
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE station ADD COLUMN latitude DECIMAL(10, 7) NULL COMMENT ''WGS84 纬度，轨迹地图用；不改变地址文本'' AFTER address',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'station' AND COLUMN_NAME = 'latitude'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 经度
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE station ADD COLUMN longitude DECIMAL(10, 7) NULL COMMENT ''WGS84 经度，轨迹地图用；与 OSM 底图一致'' AFTER latitude',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'station' AND COLUMN_NAME = 'longitude'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 写入 8 个转运中心的坐标（只改经纬度，不动站名与地址）
UPDATE station SET latitude = 39.9068844, longitude = 116.4845489 WHERE code = 'ST001';
UPDATE station SET latitude = 39.2077916, longitude = 117.1355758 WHERE code = 'ST002';
UPDATE station SET latitude = 36.6641799, longitude = 117.1456723 WHERE code = 'ST003';
UPDATE station SET latitude = 34.7221614, longitude = 113.7027748 WHERE code = 'ST004';
UPDATE station SET latitude = 31.8711900, longitude = 118.8242900 WHERE code = 'ST005';
UPDATE station SET latitude = 31.2067567, longitude = 121.2584826 WHERE code = 'ST006';
UPDATE station SET latitude = 30.6394073, longitude = 114.1765693 WHERE code = 'ST007';
UPDATE station SET latitude = 30.2815002, longitude = 120.0212235 WHERE code = 'ST008';

SELECT code, name, address, latitude, longitude
FROM station
WHERE code BETWEEN 'ST001' AND 'ST008'
ORDER BY id;

SELECT 'alter_track_map.sql 执行完成' AS summary;
