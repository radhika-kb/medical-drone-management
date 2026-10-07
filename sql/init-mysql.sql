-- ==============================================================================
-- Intelligent Medical Drone Delivery System: Bengaluru 4-Zone + Central Hub Architecture
-- MySQL Workbench 8.0+ Database Schema & Initial Bengaluru Seed Data
-- Full Build Specification conforming to System Design Document Section 15 & 16
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS medical_drone_db 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE medical_drone_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Roles & Users (Section 5: Admin, Requesting Hospital, Supplying Hospital, Central Hub)
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Central Hub & 4 Bengaluru Operational Zones (Section 4)
CREATE TABLE IF NOT EXISTS central_hubs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    status VARCHAR(50) DEFAULT 'OPERATIONAL' NOT NULL,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS zones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    hub_id BIGINT,
    center_lat DOUBLE NOT NULL,
    center_lng DOUBLE NOT NULL,
    radius_km DOUBLE NOT NULL,
    description TEXT,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    CONSTRAINT fk_zones_hub FOREIGN KEY (hub_id) REFERENCES central_hubs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Hospital directory fixtures start pending; Admin confirms each dynamic location.
CREATE TABLE IF NOT EXISTS hospitals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(50) DEFAULT 'HOSPITAL' NOT NULL,
    address TEXT NOT NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    zone_id BIGINT NULL,
    contact_number VARCHAR(50),
    verification_status VARCHAR(50) DEFAULT 'PENDING' NOT NULL, -- PENDING, APPROVED, REJECTED, SUSPENDED
    operational_status VARCHAR(50) DEFAULT 'INACTIVE' NOT NULL, -- ACTIVE, INACTIVE, BUSY
    status VARCHAR(50) DEFAULT 'INACTIVE' NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_centers_zone FOREIGN KEY (zone_id) REFERENCES zones(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) DEFAULT 'ACTIVE' NOT NULL,
    role_id BIGINT,
    hospital_id BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Medical Items Master Catalog (Section 15)
CREATE TABLE IF NOT EXISTS medical_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL, -- BLOOD, ORGAN, VACCINE, ANTIVENOM, DEFIBRILLATOR, EMERGENCY_MEDICINE
    unit VARCHAR(30) NOT NULL,     -- UNITS, BAGS, VIALS, DOSES
    description TEXT,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Hospital Inventory (Section 11 & Section 15)
CREATE TABLE IF NOT EXISTS hospital_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    medical_item_id BIGINT NOT NULL,
    available_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    expiry_date DATE NULL,
    status VARCHAR(50) DEFAULT 'AVAILABLE' NOT NULL, -- AVAILABLE, LOW_STOCK, EXPIRED, RESERVED
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_item FOREIGN KEY (medical_item_id) REFERENCES medical_items(id) ON DELETE CASCADE,
    UNIQUE KEY uk_hospital_item (hospital_id, medical_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Drone Fleet (Airborne Units) with Optimistic Lock Version
CREATE TABLE IF NOT EXISTS airborne_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drone_code VARCHAR(50) NOT NULL UNIQUE,
    model VARCHAR(100) NOT NULL,
    zone_id BIGINT NULL,
    battery_pct DOUBLE NOT NULL DEFAULT 100.0,
    max_payload_kg DOUBLE NOT NULL DEFAULT 5.0,
    max_range_km DOUBLE NOT NULL DEFAULT 45.0,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, ASSIGNED, IN_TRANSIT, CHARGING, MAINTENANCE, OFFLINE
    version BIGINT DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_airborne_units_zone FOREIGN KEY (zone_id) REFERENCES zones(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Medical Delivery Requests (Section 15 & 17)
CREATE TABLE IF NOT EXISTS medical_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_code VARCHAR(50) NOT NULL UNIQUE,
    medical_type VARCHAR(50) NOT NULL,
    medical_item_id BIGINT NULL,
    urgency VARCHAR(30) NOT NULL,
    weight_kg DOUBLE NOT NULL,
    quantity INT DEFAULT 1 NOT NULL,
    requesting_hospital_id BIGINT NULL,
    supplying_hospital_id BIGINT NULL,
    center_id BIGINT NULL, -- Backward compatibility alias
    required_by DATETIME NULL,
    notes TEXT,
    pickup_lat DOUBLE NULL, -- assigned when a supplier accepts an open request
    pickup_lng DOUBLE NULL,
    dest_lat DOUBLE NOT NULL,
    dest_lng DOUBLE NOT NULL,
    zone_id BIGINT NULL,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_req_requesting_hosp FOREIGN KEY (requesting_hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_req_supplying_hosp FOREIGN KEY (supplying_hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_req_item FOREIGN KEY (medical_item_id) REFERENCES medical_items(id),
    CONSTRAINT fk_req_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
    CONSTRAINT fk_req_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. Supply Responses (Section 5.3 & 15: Supplying Hospital Accept/Reject)
CREATE TABLE IF NOT EXISTS supply_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    supplying_hospital_id BIGINT NOT NULL,
    offered_quantity INT NOT NULL DEFAULT 1,
    response VARCHAR(50) NOT NULL, -- ACCEPTED, REJECTED
    rejection_reason TEXT NULL,
    responded_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_supply_req FOREIGN KEY (request_id) REFERENCES medical_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_supply_hosp FOREIGN KEY (supplying_hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. Missions & Routes (Section 15)
CREATE TABLE IF NOT EXISTS missions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_code VARCHAR(50) NOT NULL UNIQUE,
    request_id BIGINT NOT NULL UNIQUE,
    supplying_hospital_id BIGINT NULL,
    requesting_hospital_id BIGINT NULL,
    drone_id BIGINT NOT NULL,
    status VARCHAR(50) DEFAULT 'DISPATCHED' NOT NULL,
    distance_km DOUBLE,
    eta_minutes DOUBLE,
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    hub_decision_reason TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_mission_request FOREIGN KEY (request_id) REFERENCES medical_requests(id),
    CONSTRAINT fk_mission_supplying_hosp FOREIGN KEY (supplying_hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_mission_requesting_hosp FOREIGN KEY (requesting_hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_mission_drone FOREIGN KEY (drone_id) REFERENCES airborne_units(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS routes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_id BIGINT NOT NULL UNIQUE,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'A*-Constrained',
    total_distance_km DOUBLE NOT NULL,
    baseline_eta_minutes DOUBLE NULL,
    ml_eta_minutes DOUBLE NULL,
    final_eta_minutes DOUBLE NOT NULL,
    eta_minutes DOUBLE NOT NULL,
    battery_required_pct DOUBLE NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_route_mission FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS route_waypoints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    altitude_meters DOUBLE DEFAULT 60.0,
    type VARCHAR(50) DEFAULT 'WAYPOINT',
    CONSTRAINT fk_waypoint_route FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. Bengaluru No-Fly Zones & Geofencing (Airports, Military & VVIP Zones)
CREATE TABLE IF NOT EXISTS no_fly_zones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(50) DEFAULT 'AIRPORT' NOT NULL,
    center_lat DOUBLE NOT NULL,
    center_lng DOUBLE NOT NULL,
    radius_km DOUBLE NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. Telemetry Tracking, AI Decisions, Weather Observations
CREATE TABLE IF NOT EXISTS mission_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_id BIGINT NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    battery_pct DOUBLE NOT NULL,
    eta_minutes DOUBLE,
    status VARCHAR(50) NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_tracking_mission FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS weather_observations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    observed_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    wind_speed_kmh DOUBLE,
    visibility_km DOUBLE,
    rain_mm DOUBLE,
    temperature_c DOUBLE,
    `condition` VARCHAR(100),
    flight_condition VARCHAR(50) DEFAULT 'SAFE' NOT NULL,
    model_probability DOUBLE NULL,
    model_version VARCHAR(50) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_id BIGINT NULL,
    request_id BIGINT NULL,
    agent_name VARCHAR(50) NOT NULL,
    decision VARCHAR(100) NOT NULL,
    score DOUBLE NULL,
    reason TEXT,
    input_snapshot TEXT,
    output_snapshot TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_decision_mission FOREIGN KEY (mission_id) REFERENCES missions(id),
    CONSTRAINT fk_decision_request FOREIGN KEY (request_id) REFERENCES medical_requests(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. ML Predictions & Anomalies (Section 9, 11, 12, 13, 15)
CREATE TABLE IF NOT EXISTS demand_predictions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    medical_item_id BIGINT NOT NULL,
    hospital_id BIGINT NULL,
    zone_id BIGINT NULL,
    prediction_period VARCHAR(50) NOT NULL,
    predicted_quantity DOUBLE NOT NULL,
    model_name VARCHAR(100) DEFAULT 'XGBoostDemandRegressor',
    model_version VARCHAR(50) DEFAULT '1.0.0',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pred_item FOREIGN KEY (medical_item_id) REFERENCES medical_items(id),
    CONSTRAINT fk_pred_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_pred_zone FOREIGN KEY (zone_id) REFERENCES zones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_risk_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    medical_item_id BIGINT NOT NULL,
    zone_id BIGINT NOT NULL,
    prediction_period VARCHAR(50) NOT NULL DEFAULT 'NEXT_24H',
    predicted_demand INT NOT NULL,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL,
    shortage_quantity INT NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_evaluated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    CONSTRAINT uk_risk_hospital_item UNIQUE (hospital_id, medical_item_id),
    CONSTRAINT fk_risk_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_risk_item FOREIGN KEY (medical_item_id) REFERENCES medical_items(id),
    CONSTRAINT fk_risk_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
    INDEX idx_risk_status_level (status, risk_level),
    INDEX idx_risk_zone_item (zone_id, medical_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS eta_predictions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_id BIGINT NOT NULL,
    predicted_minutes DOUBLE NOT NULL,
    model_name VARCHAR(100) DEFAULT 'XGBoostETARegressor',
    model_version VARCHAR(50) DEFAULT '1.0.0',
    features_snapshot TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eta_mission FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mission_anomalies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_id BIGINT NOT NULL,
    anomaly_score DOUBLE NOT NULL,
    anomaly_type VARCHAR(50) NOT NULL, -- DELAY, TIMEOUT, ALTITUDE_DRIFT, BATTERY_DRAIN
    severity VARCHAR(30) NOT NULL,     -- LOW, MEDIUM, HIGH, CRITICAL
    explanation TEXT,
    detected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_anomaly_mission FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. Payments & Billing (Section 14 & 15)
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_reference VARCHAR(100) NOT NULL UNIQUE,
    request_id BIGINT NOT NULL,
    payer_hospital_id BIGINT NOT NULL,
    amount DOUBLE NOT NULL,
    currency VARCHAR(10) DEFAULT 'INR' NOT NULL,
    payment_method VARCHAR(50) DEFAULT 'RAZORPAY',
    payment_status VARCHAR(50) DEFAULT 'PENDING' NOT NULL, -- PENDING, PROCESSING, SUCCESS, FAILED, REFUNDED
    gateway_order_id VARCHAR(100) NULL,
    gateway_payment_id VARCHAR(100) NULL,
    paid_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pay_req FOREIGN KEY (request_id) REFERENCES medical_requests(id),
    CONSTRAINT fk_pay_hosp FOREIGN KEY (payer_hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. Notifications & Audit Logs (Section 15)
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    mission_id BIGINT NULL,
    type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_notif_mission FOREIGN KEY (mission_id) REFERENCES missions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NULL,
    actor_email VARCHAR(150),
    action VARCHAR(100) NOT NULL,
    entity_name VARCHAR(100),
    entity_id VARCHAR(100),
    metadata TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_actor_user FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

-- Indexes for high performance. Check first so rerunning this initializer is safe.
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'medical_requests' AND index_name = 'idx_requests_status');
SET @index_ddl = IF(@index_exists = 0, 'CREATE INDEX idx_requests_status ON medical_requests(status)', 'SELECT 1');
PREPARE index_stmt FROM @index_ddl; EXECUTE index_stmt; DEALLOCATE PREPARE index_stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'airborne_units' AND index_name = 'idx_airborne_units_zone_status');
SET @index_ddl = IF(@index_exists = 0, 'CREATE INDEX idx_airborne_units_zone_status ON airborne_units(zone_id, status)', 'SELECT 1');
PREPARE index_stmt FROM @index_ddl; EXECUTE index_stmt; DEALLOCATE PREPARE index_stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'missions' AND index_name = 'idx_missions_status');
SET @index_ddl = IF(@index_exists = 0, 'CREATE INDEX idx_missions_status ON missions(status)', 'SELECT 1');
PREPARE index_stmt FROM @index_ddl; EXECUTE index_stmt; DEALLOCATE PREPARE index_stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'mission_tracking' AND index_name = 'idx_tracking_mission');
SET @index_ddl = IF(@index_exists = 0, 'CREATE INDEX idx_tracking_mission ON mission_tracking(mission_id)', 'SELECT 1');
PREPARE index_stmt FROM @index_ddl; EXECUTE index_stmt; DEALLOCATE PREPARE index_stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'agent_decisions' AND index_name = 'idx_agent_decisions_req');
SET @index_ddl = IF(@index_exists = 0, 'CREATE INDEX idx_agent_decisions_req ON agent_decisions(request_id)', 'SELECT 1');
PREPARE index_stmt FROM @index_ddl; EXECUTE index_stmt; DEALLOCATE PREPARE index_stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'hospital_inventory' AND index_name = 'idx_inventory_hosp');
SET @index_ddl = IF(@index_exists = 0, 'CREATE INDEX idx_inventory_hosp ON hospital_inventory(hospital_id)', 'SELECT 1');
PREPARE index_stmt FROM @index_ddl; EXECUTE index_stmt; DEALLOCATE PREPARE index_stmt;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payments' AND index_name = 'idx_payments_req');
SET @index_ddl = IF(@index_exists = 0, 'CREATE INDEX idx_payments_req ON payments(request_id)', 'SELECT 1');
PREPARE index_stmt FROM @index_ddl; EXECUTE index_stmt; DEALLOCATE PREPARE index_stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- ==============================================================================
-- INITIAL BENGALURU SEED DATA
-- ==============================================================================

-- 1. System Roles (Section 5)
INSERT IGNORE INTO roles (id, name) VALUES 
(1, 'ROLE_ADMIN'),
(2, 'ROLE_REQUESTING_HOSPITAL'),
(3, 'ROLE_SUPPLYING_HOSPITAL'),
(4, 'ROLE_CENTRAL_HUB');

-- 2. Central Hub (Section 4)
INSERT IGNORE INTO central_hubs (id, name, code, latitude, longitude, status, description) VALUES
(1, 'Bengaluru Operations Hub', 'BLR-CENTRAL-HUB', 12.9750, 77.5950, 'OPERATIONAL', 'Configured operations coordination point for the Bengaluru facility network.');

-- 3. Four Operational Zones (Section 4)
INSERT IGNORE INTO zones (id, name, code, hub_id, center_lat, center_lng, radius_km, description, active) VALUES
(1, 'NORTH_ZONE', 'BLR-NORTH', 1, 13.0358, 77.5970, 18.0, 'North Zone covering Hebbal, Yelahanka, Manyata Tech Park, Sahakara Nagar, and Mathikere.', TRUE),
(2, 'EAST_ZONE', 'BLR-EAST', 1, 12.9698, 77.7500, 18.0, 'East Zone covering Whitefield, KR Puram, Marathahalli, Varthur, and ITPL corridor.', TRUE),
(3, 'SOUTH_ZONE', 'BLR-SOUTH', 1, 12.8452, 77.6602, 18.0, 'South Zone covering Electronic City, Koramangala, BTM Layout, Bannerghatta, and Jayanagar.', TRUE),
(4, 'WEST_ZONE', 'BLR-WEST', 1, 12.9982, 77.5530, 18.0, 'West Zone covering Rajajinagar, Peenya Industrial Area, Malleshwaram, and Yeshwanthpur.', TRUE);

-- 4. Verified Bengaluru healthcare directory (pending admin confirmation; locations are geocoded dynamically)
INSERT IGNORE INTO hospitals (id, name, type, address, latitude, longitude, zone_id, contact_number, verification_status, operational_status, status) VALUES
-- North Zone Hospitals
(1, 'Aster CMI Hospital', 'HOSPITAL', 'No. 43/2, New Airport Road, NH 44, Sahakar Nagar, Hebbal, Bengaluru - 560092', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(2, 'Bangalore Baptist Hospital', 'HOSPITAL', 'Bellary Road, Hebbal, Bengaluru - 560024', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(3, 'Ramaiah Medical College Hospital', 'HOSPITAL', 'MSR Nagar, MSRIT Post, Bengaluru - 560054', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(4, 'Manipal Hospital Hebbal', 'HOSPITAL', 'Kirloskar Business Park, Bellary Road, Hebbal, Bengaluru', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(5, 'Cytecare Cancer Hospital', 'HOSPITAL', 'Venkatala, near Bagalur Cross, Yelahanka, Bengaluru - 560064', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),

-- East Zone Hospitals
(6, 'Manipal Hospital Whitefield', 'HOSPITAL', 'ITPL Main Road, opposite Prestige Shantiniketan, Whitefield, Bengaluru - 560066', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(7, 'Vydehi Institute of Medical Sciences', 'HOSPITAL', '82, Nallurahalli, near BMTC 18th Depot, Whitefield, Bengaluru - 560066', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(8, 'Manipal Hospital Varthur Road', 'HOSPITAL', 'Survey No. 10P & 12P, Ramagondanahalli, Varthur Kodi, Whitefield, Bengaluru - 560066', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(9, 'Sakra World Hospital', 'HOSPITAL', 'SY No. 52/2 & 52/3, Devarabeesanahalli, Varthur Hobli, Outer Ring Road, Bengaluru - 560103', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(10, 'Cloudnine Hospital Whitefield', 'HOSPITAL', 'Nagarjuna Sai Signet, 2nd floor, 11 Whitefield Main Road, Krishnarajapura, Bengaluru - 560066', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),

-- South Zone Hospitals
(11, 'Mazumdar Shaw Medical Centre (Narayana Health)', 'HOSPITAL', '258/A, Bommasandra Industrial Area, Hosur Road, Anekal Taluk, Bengaluru - 560099', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(12, 'Fortis Hospital, Bannerghatta Road', 'HOSPITAL', '154/9, Bannerghatta Road, opposite IIM-B, Bengaluru - 560076', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(13, 'Apollo Hospitals, Bannerghatta Road', 'HOSPITAL', '154/11, IIM, opposite Krishnaraju Layout, Panduranga Nagar, Bengaluru - 560076', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(14, 'Sri Jayadeva Institute of Cardiovascular Sciences and Research', 'HOSPITAL', 'Jayanagar 9th Block, Bannerghatta Road, Bengaluru - 560069', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(15, 'St. John\'s Medical College Hospital', 'HOSPITAL', 'Sarjapur Road, Koramangala, Bengaluru - 560034', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),

-- West Zone Hospitals
(16, 'Victoria Hospital', 'HOSPITAL', 'Near City Market, Fort Road, Bengaluru - 560002', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(17, 'Bowring Hospital', 'HOSPITAL', 'Lady Curzon Road, Shivaji Nagar, Tasker Town, Bengaluru - 560001', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),
(18, 'K.C. General Hospital', 'HOSPITAL', '#89, 5th Cross Road, behind Police Station, Malleshwaram, Bengaluru - 560003', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE'),

-- The verified BMST listing is a blood-bank institution only; it has no stock preloaded.
(21, 'Rotary Bangalore-TTK Blood Centre (BMST)', 'BLOOD_BANK', 'New Thippasandra Main Road, HAL 3rd Stage, Bengaluru - 560075', NULL, NULL, NULL, NULL, 'PENDING', 'INACTIVE', 'INACTIVE');

-- 5. Administrative bootstrap users are created by Spring Boot DataInitializer with BCrypt passwords.
-- Requester and supplier accounts are created through facility-linked registration.

-- 6. Medical Items Master Data (Section 15)
INSERT IGNORE INTO medical_items (id, code, name, category, unit, description, active) VALUES
(1, 'MED-BLD-ONEG', 'O-Negative Packed Red Blood Cells', 'BLOOD', 'Units', 'Universal emergency donor blood units.', TRUE),
(2, 'MED-BLD-APOS', 'A-Positive Whole Blood', 'BLOOD', 'Units', 'Standard trauma transfusion blood.', TRUE),
(3, 'MED-PLM-AB', 'AB-Universal Fresh Frozen Plasma', 'BLOOD', 'Bags', 'Critical burn and coagulopathy plasma.', TRUE),
(4, 'MED-ORG-TRANSPORT', 'Organ Transport Preservation Container', 'ORGAN_TRANSPORT', 'Units', 'Transport equipment for organ transfers; not an organ inventory record.', TRUE),
(5, 'MED-AVN-POLY', 'Polyvalent Snake Antivenom 10ml', 'ANTIVENOM', 'Vials', 'Neutralizes Cobra, Krait, Russell Viper venoms.', TRUE),
(6, 'MED-AED-AUTO', 'Automated External Defibrillator Unit', 'DEFIBRILLATOR', 'Units', 'Lightweight portable cardiac defibrillator.', TRUE),
(7, 'MED-VAC-RAB', 'Rabies Post-Exposure Prophylaxis Vaccine', 'VACCINE', 'Doses', 'Urgent immunization doses.', TRUE),
(8, 'MED-INS-HUM', 'Human Actrapid Insulin 100IU', 'EMERGENCY_MEDICINE', 'Vials', 'Critical diabetic ketoacidosis emergency supply.', TRUE);

-- 7. No inventory quantities are seeded. Facilities must enter their actual stock.

-- 8. No guessed airspace exclusion buffers are seeded.
-- Existing databases retain legacy rows, which the application ignores.

-- 9. Simulated software fleet (no physical aircraft registrations or aircraft models are represented)
INSERT IGNORE INTO airborne_units (id, drone_code, model, zone_id, battery_pct, max_payload_kg, max_range_km, latitude, longitude, status) VALUES
-- North Zone Fleet
(1, 'DRONE-N-01', 'Medical Quadcopter - Class M', 1, 95.0, 5.0, 45.0, 13.0358, 77.5970, 'AVAILABLE'),
(2, 'DRONE-N-02', 'Medical Quadcopter - Class S', 1, 88.0, 4.5, 40.0, 13.0450, 77.5920, 'AVAILABLE'),
(3, 'DRONE-N-03', 'Medical Quadcopter - Class L', 1, 92.0, 6.0, 50.0, 13.0312, 77.5649, 'AVAILABLE'),

-- East Zone Fleet
(4, 'DRONE-E-01', 'Medical Quadcopter - Class M', 2, 94.0, 5.0, 45.0, 12.9867, 77.7288, 'AVAILABLE'),
(5, 'DRONE-E-02', 'Medical Quadcopter - Class S', 2, 85.0, 4.5, 40.0, 12.9698, 77.7500, 'AVAILABLE'),
(6, 'DRONE-E-03', 'Medical Quadcopter - Class L', 2, 90.0, 6.0, 50.0, 12.9288, 77.6835, 'AVAILABLE'),

-- South Zone Fleet
(7, 'DRONE-S-01', 'Medical Quadcopter - Class M', 3, 98.0, 5.0, 45.0, 12.8252, 77.6896, 'AVAILABLE'),
(8, 'DRONE-S-02', 'Medical Quadcopter - Class S', 3, 78.0, 4.5, 40.0, 12.8938, 77.5971, 'AVAILABLE'),
(9, 'DRONE-S-03', 'Medical Quadcopter - Class L', 3, 91.0, 6.0, 50.0, 12.9344, 77.6190, 'AVAILABLE'),

-- West Zone Fleet
(10, 'DRONE-W-01', 'Medical Quadcopter - Class M', 4, 93.0, 5.0, 45.0, 12.9634, 77.5750, 'AVAILABLE'),
(11, 'DRONE-W-02', 'Medical Quadcopter - Class S', 4, 82.0, 4.5, 40.0, 12.9988, 77.5532, 'AVAILABLE'),
(12, 'DRONE-W-03', 'Medical Quadcopter - Class L', 4, 89.0, 6.0, 50.0, 13.0215, 77.5455, 'AVAILABLE'),

-- Central Hub Reserve & Heavy Fallback Fleet
(13, 'DRONE-HUB-01', 'Medical Quadcopter - Class XL', NULL, 100.0, 12.0, 75.0, 12.9750, 77.5950, 'AVAILABLE'),
(14, 'DRONE-HUB-02', 'Medical Quadcopter - Class XL', NULL, 100.0, 12.0, 75.0, 12.9750, 77.5950, 'AVAILABLE');

-- 10. Delivery pricing defaults; admins may edit or deactivate them via /api/pricing-rules.
INSERT IGNORE INTO pricing_rules
    (rule_code, rule_type, medical_category, urgency, base_fee, per_km_rate, per_kg_rate, category_fee, urgency_multiplier, priority, active)
VALUES
('BASE_STANDARD', 'BASE', NULL, NULL, 450.00, 40.00, 80.00, 0.00, 1.000, 0, TRUE),
('CATEGORY_ORGAN', 'CATEGORY', 'ORGAN', NULL, 0.00, 0.00, 0.00, 500.00, 1.000, 0, TRUE),
('CATEGORY_BLOOD', 'CATEGORY', 'BLOOD', NULL, 0.00, 0.00, 0.00, 200.00, 1.000, 0, TRUE),
('CATEGORY_ANTIVENOM', 'CATEGORY', 'ANTIVENOM', NULL, 0.00, 0.00, 0.00, 150.00, 1.000, 0, TRUE),
('URGENCY_CRITICAL', 'URGENCY', NULL, 'CRITICAL', 0.00, 0.00, 0.00, 0.00, 1.500, 0, TRUE),
('URGENCY_HIGH', 'URGENCY', NULL, 'HIGH', 0.00, 0.00, 0.00, 0.00, 1.250, 0, TRUE);
