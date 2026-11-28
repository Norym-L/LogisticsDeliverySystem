-- 为已有物流库增加“期末六项”所需的列、状态与约束，不删除数据。
-- 前置：已经执行过 schema.sql / alter_pricing.sql。
-- 可重复执行：已存在的列、外键会被跳过；状态 CHECK 会先删后建。

USE logistics;

SET @db := DATABASE();

-- 1. 用户取消时间
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN cancel_time DATETIME NULL COMMENT ''用户取消时间'' AFTER receive_time',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'cancel_time'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 最终核价由始发站操作员确认时记录操作员；快递员确认仍用 price_confirmed_by
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN price_confirmed_operator BIGINT UNSIGNED NULL COMMENT ''核价操作员（始发站收件时）'' AFTER price_confirmed_by',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'price_confirmed_operator'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 始发站确认收件时间
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD COLUMN receipt_confirmed_at DATETIME NULL COMMENT ''始发站确认收件时间'' AFTER price_confirmed_operator',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND COLUMN_NAME = 'receipt_confirmed_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 外键：核价操作员
SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE delivery_order ADD CONSTRAINT fk_order_price_operator FOREIGN KEY (price_confirmed_operator) REFERENCES operator (id)',
              'SELECT 1')
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order' AND CONSTRAINT_NAME = 'fk_order_price_operator'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. 状态 CHECK 增加 AWAITING_RECEIPT / CANCELLED
SET @sql := (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE delivery_order DROP CHECK ck_order_state',
              'SELECT 1')
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'delivery_order'
      AND CONSTRAINT_NAME = 'ck_order_state' AND CONSTRAINT_TYPE = 'CHECK'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE delivery_order
    ADD CONSTRAINT ck_order_state CHECK (state IN ('WAITING', 'AWAITING_RECEIPT', 'APPROVED',
                                                   'REJECTED', 'SENDING', 'DELIVERING',
                                                   'RECEIVED', 'REFUSED', 'CANCELLED'));

SELECT 'alter_final_six.sql 执行完成' AS summary;
