-- Phase 1 database finalization. Apply once to an existing medical_drone_db
-- before deploying the application build that maps to hospitals/zones/airborne_units.
-- Renames retain all existing rows and InnoDB updates foreign-key references.
USE medical_drone_db;

SET @schema_name = DATABASE();

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'medical_centers')
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'hospitals'),
    'RENAME TABLE medical_centers TO hospitals', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'drone_zones')
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'zones'),
    'RENAME TABLE drone_zones TO zones', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'drones')
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'airborne_units'),
    'RENAME TABLE drones TO airborne_units', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'airborne_units' AND index_name = 'idx_drones_zone_status')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'airborne_units' AND index_name = 'idx_airborne_units_zone_status'),
    'ALTER TABLE airborne_units RENAME INDEX idx_drones_zone_status TO idx_airborne_units_zone_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS pricing_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code VARCHAR(80) NOT NULL UNIQUE,
    rule_type VARCHAR(20) NOT NULL,
    medical_category VARCHAR(50) NULL,
    urgency VARCHAR(30) NULL,
    base_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    per_km_rate DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    per_kg_rate DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    category_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    urgency_multiplier DECIMAL(6,3) NOT NULL DEFAULT 1.000,
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_pricing_rule_type CHECK (rule_type IN ('BASE', 'CATEGORY', 'URGENCY')),
    CONSTRAINT chk_pricing_rule_selector CHECK (
        (rule_type = 'BASE' AND medical_category IS NULL AND urgency IS NULL)
        OR (rule_type = 'CATEGORY' AND medical_category IS NOT NULL AND urgency IS NULL)
        OR (rule_type = 'URGENCY' AND medical_category IS NULL AND urgency IS NOT NULL)
    ),
    INDEX idx_pricing_rules_active_type (active, rule_type, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO pricing_rules
    (rule_code, rule_type, medical_category, urgency, base_fee, per_km_rate, per_kg_rate, category_fee, urgency_multiplier, priority, active)
VALUES
('BASE_STANDARD', 'BASE', NULL, NULL, 450.00, 40.00, 80.00, 0.00, 1.000, 0, TRUE),
('CATEGORY_ORGAN', 'CATEGORY', 'ORGAN', NULL, 0.00, 0.00, 0.00, 500.00, 1.000, 0, TRUE),
('CATEGORY_BLOOD', 'CATEGORY', 'BLOOD', NULL, 0.00, 0.00, 0.00, 200.00, 1.000, 0, TRUE),
('CATEGORY_ANTIVENOM', 'CATEGORY', 'ANTIVENOM', NULL, 0.00, 0.00, 0.00, 150.00, 1.000, 0, TRUE),
('URGENCY_CRITICAL', 'URGENCY', NULL, 'CRITICAL', 0.00, 0.00, 0.00, 0.00, 1.500, 0, TRUE),
('URGENCY_HIGH', 'URGENCY', NULL, 'HIGH', 0.00, 0.00, 0.00, 0.00, 1.250, 0, TRUE);
