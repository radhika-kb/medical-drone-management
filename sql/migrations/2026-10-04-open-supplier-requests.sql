-- Allows a requesting facility to publish a request before a supplier is known.
-- The supplier's actual pickup coordinates are written when a supplier accepts.
USE medical_drone_db;

ALTER TABLE medical_requests
    MODIFY COLUMN pickup_lat DOUBLE NULL,
    MODIFY COLUMN pickup_lng DOUBLE NULL;
