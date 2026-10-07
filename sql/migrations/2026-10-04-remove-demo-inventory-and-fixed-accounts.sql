-- Apply to an existing medical_drone_db when the demo seed has already run.
-- Deletes only the exact inventory tuples shipped as sample data in init-mysql.sql.
-- Any row whose quantity/date was changed by a facility is preserved.
USE medical_drone_db;

DELETE FROM hospital_inventory
WHERE (id, hospital_id, medical_item_id, available_quantity, reserved_quantity, expiry_date) IN (
    (1, 11, 1, 45, 0, '2027-05-30'),
    (2, 11, 2, 60, 0, '2027-06-15'),
    (3, 11, 4, 3, 0, '2026-10-10'),
    (4, 11, 5, 50, 0, '2028-01-01'),
    (5, 11, 6, 8, 0, '2030-01-01'),
    (6, 1, 1, 15, 0, '2027-05-30'),
    (7, 1, 5, 20, 0, '2028-01-01'),
    (8, 1, 7, 30, 0, '2027-11-20'),
    (9, 16, 1, 35, 0, '2027-04-10'),
    (10, 16, 5, 40, 0, '2028-02-15'),
    (11, 16, 4, 2, 0, '2026-10-08'),
    (12, 21, 1, 80, 0, '2027-06-01'),
    (13, 21, 2, 120, 0, '2027-06-01'),
    (14, 21, 3, 40, 0, '2027-08-01'),
    (15, 26, 1, 95, 0, '2027-07-01'),
    (16, 26, 2, 140, 0, '2027-07-01'),
    (17, 6, 1, 20, 0, '2027-05-15'),
    (18, 6, 6, 5, 0, '2029-01-01'),
    (19, 6, 8, 25, 0, '2027-12-01')
);

-- Remove only the fixed requester/supplier sample users; registered staff are unaffected.
DELETE FROM users
WHERE (email = 'requester@meddrone.in' AND name = 'Requesting Doctor (Aster CMI)')
   OR (email = 'supplier@meddrone.in' AND name = 'Supplying Pharmacist (Narayana)')
   OR (email = 'hospital@meddrone.in' AND name = 'General Hospital Staff')
   OR (email = 'admin@meddrone.in' AND password_hash = '$2a$10$w09ZlO7d0m/EcewTzszAfeqYF30N4eRjGzS4FqJ3Z1sX8P2Kk9Bqa')
   OR (email = 'operator@meddrone.in' AND password_hash = '$2a$10$w09ZlO7d0m/EcewTzszAfeqYF30N4eRjGzS4FqJ3Z1sX8P2Kk9Bqa');

-- Replace inaccurate names from earlier demo seed revisions with verified local services.
UPDATE medical_centers
SET name = 'Manipal Hospital Varthur Road'
WHERE name = 'Columbia Asia Hospital (Manipal Varthur)';

UPDATE medical_centers
SET name = 'Ramaiah Advanced Learning Centre Tissue Bank',
    type = 'TISSUE_BANK',
    address = 'MSRIT Post, New BEL Road, Mathikere, Bengaluru - 560054',
    latitude = 13.0312,
    longitude = 77.5649,
    zone_id = 1,
    contact_number = '+91-80-2360-8888'
WHERE name = 'Red Cross Blood Centre Karnataka';

UPDATE medical_centers
SET name = 'Cytecare Cancer Hospital Blood Storage Centre',
    type = 'BLOOD_BANK',
    address = 'Near Venkatala, NH 44, Yelahanka, Bengaluru - 560064',
    latitude = 13.1250,
    longitude = 77.6040,
    zone_id = 1,
    contact_number = '+91-80-2217-6767'
WHERE name = 'Cloudnine Hospital Blood Storage Centre';

UPDATE medical_items
SET code = 'MED-ORG-TRANSPORT',
    name = 'Organ Transport Preservation Container',
    category = 'ORGAN_TRANSPORT',
    description = 'Transport equipment for organ transfers; not an organ inventory record.'
WHERE id = 4 AND code = 'MED-ORG-KID';
