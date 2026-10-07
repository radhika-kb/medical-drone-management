-- One-time migration for the legacy seeded hospital directory.
-- Run against medical_drone_db before starting the updated backend.
-- Do not rerun after admins have confirmed facility locations.
USE medical_drone_db;

ALTER TABLE hospitals
    MODIFY latitude DOUBLE NULL,
    MODIFY longitude DOUBLE NULL,
    MODIFY zone_id BIGINT NULL;

UPDATE hospitals
SET latitude = NULL,
    longitude = NULL,
    zone_id = NULL,
    verification_status = 'PENDING',
    operational_status = 'INACTIVE',
    status = 'INACTIVE'
WHERE (id, name) IN (
    (1, 'Aster CMI Hospital'),
    (2, 'Bangalore Baptist Hospital'),
    (3, 'Ramaiah Memorial Hospital'),
    (4, 'Manipal Hospital Hebbal'),
    (5, 'Cytecare Cancer Hospital'),
    (6, 'Manipal Hospital Whitefield'),
    (7, 'Vydehi Institute of Medical Sciences'),
    (8, 'Manipal Hospital Varthur Road'),
    (9, 'Sakra World Hospital'),
    (10, 'Cloudnine Hospital Whitefield'),
    (11, 'Narayana Health City (Mazumdar Shaw)'),
    (12, 'Fortis Hospital Bannerghatta'),
    (13, 'Apollo Hospitals Bannerghatta'),
    (14, 'Sri Jayadeva Institute of Cardiology'),
    (15, 'St. John\'s Medical College Hospital'),
    (16, 'Victoria Hospital (BMCRI)'),
    (17, 'Bowring and Lady Curzon Hospital'),
    (18, 'KC General Hospital'),
    (19, 'People Tree Hospitals'),
    (20, 'Fortis Hospital Rajajinagar'),
    (21, 'Rotary Bangalore-TTK Blood Bank (BMST)'),
    (22, 'Bangalore Baptist Hospital Blood Bank'),
    (23, 'Bangalore Medical Services Trust Tissue Bank'),
    (24, 'Aster CMI Hospital Blood Centre'),
    (25, 'Manipal Hospitals Whitefield Blood Storage Centre'),
    (26, 'Ramaiah Advanced Learning Centre Tissue Bank'),
    (27, 'People Tree Hospitals Blood Storage Centre'),
    (28, 'Cytecare Cancer Hospital Blood Storage Centre')
);
