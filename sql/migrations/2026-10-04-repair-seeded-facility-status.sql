-- Repair only the known seeded Bengaluru facilities whose status columns are blank.
-- Pending, rejected, suspended, and non-seeded facility records are left unchanged.
USE medical_drone_db;

UPDATE medical_centers
SET verification_status = 'APPROVED', operational_status = 'ACTIVE', status = 'ACTIVE'
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
)
AND (verification_status IS NULL OR TRIM(verification_status) = '')
AND (operational_status IS NULL OR TRIM(operational_status) = '' OR UPPER(operational_status) = 'ACTIVE')
AND (status IS NULL OR TRIM(status) = '' OR UPPER(status) = 'ACTIVE');

SELECT id, name, verification_status, operational_status, status
FROM medical_centers
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
)
ORDER BY id;
