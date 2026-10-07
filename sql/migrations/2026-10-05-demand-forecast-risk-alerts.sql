-- Run once on an existing MySQL database to enable hospital-level forecast history
-- and deduplicated inventory-risk alerts.

ALTER TABLE demand_predictions
    ADD COLUMN hospital_id BIGINT NULL AFTER medical_item_id,
    ADD CONSTRAINT fk_pred_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    ADD INDEX idx_demand_hospital_item_created (hospital_id, medical_item_id, created_at);

CREATE TABLE inventory_risk_alerts (
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
