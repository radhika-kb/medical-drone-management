-- Forward-only correction for installations created from older demo seeds.
-- This migration is intentionally NOT applied automatically. It only adjusts known
-- unverified seed rows that are still pending and inactive; administrator-approved
-- or operational records are left untouched. Existing facility coordinates are kept
-- because they may have been geocoded and confirmed by an administrator.

UPDATE hospitals
SET name = CASE id
        WHEN 3 THEN 'Ramaiah Medical College Hospital'
        WHEN 11 THEN 'Mazumdar Shaw Medical Centre (Narayana Health)'
        WHEN 12 THEN 'Fortis Hospital, Bannerghatta Road'
        WHEN 13 THEN 'Apollo Hospitals, Bannerghatta Road'
        WHEN 14 THEN 'Sri Jayadeva Institute of Cardiovascular Sciences and Research'
        WHEN 16 THEN 'Victoria Hospital'
        WHEN 17 THEN 'Bowring Hospital'
        WHEN 18 THEN 'K.C. General Hospital'
        WHEN 21 THEN 'Rotary Bangalore-TTK Blood Centre (BMST)'
        ELSE name
    END,
    address = CASE id
        WHEN 3 THEN 'MSR Nagar, MSRIT Post, Bengaluru - 560054'
        WHEN 4 THEN 'Kirloskar Business Park, Bellary Road, Hebbal, Bengaluru'
        WHEN 5 THEN 'Venkatala, near Bagalur Cross, Yelahanka, Bengaluru - 560064'
        WHEN 6 THEN 'ITPL Main Road, opposite Prestige Shantiniketan, Whitefield, Bengaluru - 560066'
        WHEN 7 THEN '82, Nallurahalli, near BMTC 18th Depot, Whitefield, Bengaluru - 560066'
        WHEN 8 THEN 'Survey No. 10P & 12P, Ramagondanahalli, Varthur Kodi, Whitefield, Bengaluru - 560066'
        WHEN 9 THEN 'SY No. 52/2 & 52/3, Devarabeesanahalli, Varthur Hobli, Outer Ring Road, Bengaluru - 560103'
        WHEN 10 THEN 'Nagarjuna Sai Signet, 2nd floor, 11 Whitefield Main Road, Krishnarajapura, Bengaluru - 560066'
        WHEN 11 THEN '258/A, Bommasandra Industrial Area, Hosur Road, Anekal Taluk, Bengaluru - 560099'
        WHEN 12 THEN '154/9, Bannerghatta Road, opposite IIM-B, Bengaluru - 560076'
        WHEN 13 THEN '154/11, IIM, opposite Krishnaraju Layout, Panduranga Nagar, Bengaluru - 560076'
        WHEN 14 THEN 'Jayanagar 9th Block, Bannerghatta Road, Bengaluru - 560069'
        WHEN 15 THEN 'Sarjapur Road, Koramangala, Bengaluru - 560034'
        WHEN 16 THEN 'Near City Market, Fort Road, Bengaluru - 560002'
        WHEN 17 THEN 'Lady Curzon Road, Shivaji Nagar, Tasker Town, Bengaluru - 560001'
        WHEN 18 THEN '#89, 5th Cross Road, behind Police Station, Malleshwaram, Bengaluru - 560003'
        ELSE address
    END,
    contact_number = NULL
WHERE verification_status = 'PENDING'
  AND operational_status = 'INACTIVE'
  AND status = 'INACTIVE'
  AND id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,21)
  AND name IN (
      'Aster CMI Hospital', 'Bangalore Baptist Hospital', 'Ramaiah Memorial Hospital',
      'Manipal Hospital Hebbal', 'Cytecare Cancer Hospital', 'Manipal Hospital Whitefield',
      'Vydehi Institute of Medical Sciences', 'Manipal Hospital Varthur Road',
      'Sakra World Hospital', 'Cloudnine Hospital Whitefield',
      'Narayana Health City (Mazumdar Shaw)', 'Fortis Hospital Bannerghatta',
      'Apollo Hospitals Bannerghatta', 'Sri Jayadeva Institute of Cardiology',
      'St. John''s Medical College Hospital', 'Victoria Hospital (BMCRI)',
      'Bowring and Lady Curzon Hospital', 'KC General Hospital',
      'Rotary Bangalore-TTK Blood Bank (BMST)'
  );

-- These old entries were either unsupported facility sub-types or separate duplicates.
-- Keep them visible to administrators as rejected records rather than deleting rows or
-- risking references to any historical requests/inventory.
UPDATE hospitals
SET verification_status = 'REJECTED', operational_status = 'INACTIVE', status = 'INACTIVE'
WHERE verification_status = 'PENDING'
  AND operational_status = 'INACTIVE'
  AND status = 'INACTIVE'
  AND name IN (
      'People Tree Hospitals', 'Fortis Hospital Rajajinagar',
      'Bangalore Baptist Hospital Blood Bank', 'Bangalore Medical Services Trust Tissue Bank',
      'Aster CMI Hospital Blood Centre', 'Manipal Hospitals Whitefield Blood Storage Centre',
      'Ramaiah Advanced Learning Centre Tissue Bank', 'People Tree Hospitals Blood Storage Centre',
      'Cytecare Cancer Hospital Blood Storage Centre'
  );

UPDATE central_hubs
SET name = 'Simulated Bengaluru Central Hub',
    description = 'Software simulation hub used to display Bengaluru logistics scenarios.'
WHERE id = 1 AND code = 'BLR-CENTRAL-HUB';

UPDATE no_fly_zones SET
    name = CASE id
        WHEN 1 THEN 'Simulation buffer near HAL'
        WHEN 2 THEN 'Simulation buffer near Yelahanka'
        WHEN 3 THEN 'Simulation buffer near Devanahalli'
        WHEN 4 THEN 'Simulation buffer near central government district'
        WHEN 5 THEN 'Simulation buffer near Jakkur'
        ELSE name
    END
WHERE id BETWEEN 1 AND 5;

UPDATE airborne_units
SET model = CASE
    WHEN drone_code IN ('DRONE-N-01','DRONE-E-01','DRONE-S-01','DRONE-W-01') THEN 'Simulated Medical Quadcopter - Class M'
    WHEN drone_code IN ('DRONE-N-02','DRONE-E-02','DRONE-S-02','DRONE-W-02') THEN 'Simulated Medical Quadcopter - Class S'
    WHEN drone_code IN ('DRONE-N-03','DRONE-E-03','DRONE-S-03','DRONE-W-03') THEN 'Simulated Medical Quadcopter - Class L'
    WHEN drone_code IN ('DRONE-HUB-01','DRONE-HUB-02') THEN 'Simulated Medical Quadcopter - Class XL'
    ELSE model
END
WHERE drone_code IN ('DRONE-N-01','DRONE-E-01','DRONE-S-01','DRONE-W-01',
                     'DRONE-N-02','DRONE-E-02','DRONE-S-02','DRONE-W-02',
                     'DRONE-N-03','DRONE-E-03','DRONE-S-03','DRONE-W-03',
                     'DRONE-HUB-01','DRONE-HUB-02');
