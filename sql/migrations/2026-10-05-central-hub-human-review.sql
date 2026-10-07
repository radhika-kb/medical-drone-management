-- Phase 7: persist the Central Hub decision note on each mission.
-- JPA schema update adds this automatically; this idempotent migration is for
-- operators who apply schema changes through MySQL Workbench instead.
USE medical_drone_db;

SET @schema_name = DATABASE();
SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'missions'
          AND column_name = 'hub_decision_reason'
    ),
    'ALTER TABLE missions ADD COLUMN hub_decision_reason TEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
