-- 为已有物流库安全增加运费相关字段，不删除数据。
-- 可重复执行：已存在的列和外键会被跳过。

USE logistics;

SET @db := DATABASE();

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN volume_cm3 DECIMAL(12,2) NULL COMMENT ''用户申报体积，立方厘米'' AFTER weight',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'volume_cm3'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN quoted_price DECIMAL(10,2) NULL COMMENT ''下单预估价'' AFTER volume_cm3',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'quoted_price'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN actual_volume_cm3 DECIMAL(12,2) NULL COMMENT ''快递员实测体积，立方厘米'' AFTER quoted_price',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'actual_volume_cm3'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN actual_weight DECIMAL(8,2) NULL COMMENT ''快递员实测质量 kg'' AFTER actual_volume_cm3',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'actual_weight'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN price DECIMAL(10,2) NULL COMMENT ''最终运费，核价后写入'' AFTER actual_weight',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'price'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN price_confirmed_at DATETIME NULL COMMENT ''核价时间'' AFTER price',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'price_confirmed_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN price_confirmed_by BIGINT UNSIGNED NULL COMMENT ''核价快递员'' AFTER price_confirmed_at',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'price_confirmed_by'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD CONSTRAINT fk_order_price_by FOREIGN KEY (price_confirmed_by) REFERENCES postman (id)',
              'SELECT 1')
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND CONSTRAINT_NAME = 'fk_order_price_by'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
