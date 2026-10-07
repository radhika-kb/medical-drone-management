package com.medicaldrone.init;

import com.medicaldrone.model.*;
import com.medicaldrone.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CentralHubRepository centralHubRepository;
    private final DroneZoneRepository zoneRepository;
    private final MedicalCenterRepository centerRepository;
    private final DroneRepository droneRepository;
    private final MedicalItemRepository medicalItemRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository,
                           CentralHubRepository centralHubRepository, DroneZoneRepository zoneRepository,
                           MedicalCenterRepository centerRepository, DroneRepository droneRepository,
                           MedicalItemRepository medicalItemRepository,
                           PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.centralHubRepository = centralHubRepository;
        this.zoneRepository = zoneRepository;
        this.centerRepository = centerRepository;
        this.droneRepository = droneRepository;
        this.medicalItemRepository = medicalItemRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (centralHubRepository.count() == 0) {
            initBengaluruZonesAndHub();
        }

        if (medicalItemRepository.count() == 0) {
            initMedicalItems();
        }

        initRolesAndUsers();
        repairBlankSeededFacilityStatuses();

        logger.info("Intelligent Medical Drone Delivery System: Bengaluru 4-Zone + Central Hub initialized successfully.");
    }

    /**
     * Older Workbench imports can have directory rows but blank status columns.
     * Repair only located rows with blank statuses; never override an admin decision.
     */
    private void repairBlankSeededFacilityStatuses() {
        Set<String> seededFacilityNames = Set.of(
                "Aster CMI Hospital",
                "Bangalore Baptist Hospital",
                "Ramaiah Medical College Hospital",
                "Manipal Hospital Hebbal",
                "Cytecare Cancer Hospital",
                "Manipal Hospital Whitefield",
                "Vydehi Institute of Medical Sciences",
                "Manipal Hospital Varthur Road",
                "Sakra World Hospital",
                "Cloudnine Hospital Whitefield",
                "Mazumdar Shaw Medical Centre (Narayana Health)",
                "Fortis Hospital, Bannerghatta Road",
                "Apollo Hospitals, Bannerghatta Road",
                "Sri Jayadeva Institute of Cardiovascular Sciences and Research",
                "St. John's Medical College Hospital",
                "Victoria Hospital",
                "Bowring Hospital",
                "K.C. General Hospital",
                "Rotary Bangalore-TTK Blood Centre (BMST)"
        );

        int repaired = 0;
        for (MedicalCenter center : centerRepository.findAll()) {
            if (!seededFacilityNames.contains(center.getName())) {
                continue;
            }
            if (center.getLatitude() == null || center.getLongitude() == null || center.getZone() == null) {
                continue;
            }
            if (center.getVerificationStatus() != null && !center.getVerificationStatus().isBlank()) {
                continue;
            }
            if (hasNonActiveStatus(center.getOperationalStatus()) || hasNonActiveStatus(center.getStatus())) {
                continue;
            }

            center.setVerificationStatus("APPROVED");
            center.setOperationalStatus("ACTIVE");
            center.setStatus("ACTIVE");
            centerRepository.save(center);
            repaired++;
        }
        if (repaired > 0) {
            logger.warn("Repaired blank status fields for {} known seeded Bengaluru facilities.", repaired);
        }
    }

    private boolean hasNonActiveStatus(String status) {
        return status != null && !status.isBlank() && !"ACTIVE".equalsIgnoreCase(status);
    }

    private void initRolesAndUsers() {
        Role adminRole = getOrCreateRole("ROLE_ADMIN");
        getOrCreateRole(Role.REQUESTING_HOSPITAL);
        getOrCreateRole(Role.SUPPLYING_HOSPITAL);
        Role centralHubRole = getOrCreateRole(Role.CENTRAL_HUB);
        migrateLegacyOperatorRole(centralHubRole);

        createUserIfMissing("System Administrator", "admin@meddrone.in", "admin123", adminRole, null);
        createUserIfMissing("Central Hub Fleet Operator", "operator@meddrone.in", "operator123", centralHubRole, null);
    }

    private void migrateLegacyOperatorRole(Role centralHubRole) {
        Role legacyRole = roleRepository.findByName(Role.OPERATOR).orElse(null);
        if (legacyRole == null) return;
        for (User user : userRepository.findAll()) {
            if (user.getRole() != null && legacyRole.getId().equals(user.getRole().getId())) {
                user.setRole(centralHubRole);
                userRepository.save(user);
            }
        }
        userRepository.flush();
        roleRepository.delete(legacyRole);
    }

    private Role getOrCreateRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
    }

    private void createUserIfMissing(String name, String email, String rawPassword, Role role, MedicalCenter hospital) {
        if (!userRepository.existsByEmail(email)) {
            User u = new User(name, email, passwordEncoder.encode(rawPassword), role, hospital);
            userRepository.save(u);
        }
    }

    private void initMedicalItems() {
        medicalItemRepository.save(new MedicalItem("MED-BLD-OPOS", "O Positive Whole Blood", "BLOOD", "UNITS", "Universal whole blood pack (450ml)"));
        medicalItemRepository.save(new MedicalItem("MED-BLD-ONEG", "O Negative Whole Blood", "BLOOD", "UNITS", "Universal emergency donor blood pack (450ml)"));
        medicalItemRepository.save(new MedicalItem("MED-BLD-APOS", "A Positive Blood", "BLOOD", "UNITS", "Type A+ RBC packed unit (350ml)"));
        medicalItemRepository.save(new MedicalItem("MED-PLT-SDP", "Single Donor Platelets (SDP)", "BLOOD", "UNITS", "Emergency platelets concentrate for hemorrhage"));
        medicalItemRepository.save(new MedicalItem("MED-ORG-KID", "Kidney Transport Preservation Container", "ORGAN", "UNITS", "Insulated transport container"));
        medicalItemRepository.save(new MedicalItem("MED-ORG-COR", "Cornea Transport Container", "ORGAN", "UNITS", "Temperature-controlled transport container"));
        medicalItemRepository.save(new MedicalItem("MED-VAC-RAB", "Anti-Rabies Vaccine & Immunoglobulin", "VACCINE", "VIALS", "Emergency post-exposure rabies prophylaxis"));
        medicalItemRepository.save(new MedicalItem("MED-VAC-COV", "COVID-19 Booster Vaccine", "VACCINE", "VIALS", "Cold-chain mRNA vaccine batch"));
        medicalItemRepository.save(new MedicalItem("MED-DRG-AVM", "Polyvalent Snake Antivenom", "ANTIVENOM", "VIALS", "Lyophilized antivenom for Russell viper/Cobra bites"));
        medicalItemRepository.save(new MedicalItem("MED-DRG-EPI", "Epinephrine Auto-Injectors (0.3mg)", "EMERGENCY_MEDICINE", "PACKS", "Immediate anaphylaxis and cardiac resuscitation drug"));
        medicalItemRepository.save(new MedicalItem("MED-DEV-AED", "Automated External Defibrillator Pads", "DEFIBRILLATOR", "PACKS", "Adult/Pediatric AED electrode pads with quick connector"));
    }

    private void initBengaluruZonesAndHub() {
        // 1. Central Hub
        CentralHub hub = new CentralHub(
                "Bengaluru Operations Hub",
                "BLR-CENTRAL-HUB",
                12.9750,
                77.6000,
                "Configured operations coordination point for the Bengaluru facility network."
        );
        centralHubRepository.save(hub);

        // 2. The 4 Operational Zones
        DroneZone northZone = zoneRepository.save(new DroneZone(
                "NORTH_ZONE", "BLR-NORTH", hub, 13.0358, 77.5970, 18.0,
                "North Operational Zone covering Hebbal, Yelahanka, Manyata Tech Park, and Sahakara Nagar."
        ));

        DroneZone eastZone = zoneRepository.save(new DroneZone(
                "EAST_ZONE", "BLR-EAST", hub, 12.9698, 77.7500, 18.0,
                "East Operational Zone covering Whitefield, KR Puram, Marathahalli, and ITPL corridor."
        ));

        DroneZone southZone = zoneRepository.save(new DroneZone(
                "SOUTH_ZONE", "BLR-SOUTH", hub, 12.8452, 77.6602, 18.0,
                "South Operational Zone covering Electronic City, Koramangala, BTM Layout, and Jayanagar."
        ));

        DroneZone westZone = zoneRepository.save(new DroneZone(
                "WEST_ZONE", "BLR-WEST", hub, 12.9982, 77.5530, 18.0,
                "West Operational Zone covering Rajajinagar, Peenya Industrial Area, Malleshwaram, and Yeshwanthpur."
        ));

        // Hospitals are onboarded by Admin after backend geocoding and map-pin confirmation.

        // 4. Drone Fleet
        // North Zone Drones
        droneRepository.save(new Drone("DRONE-N-01", "Medical Quadcopter - Class M", northZone, 95.0, 5.0, 45.0, 13.0358, 77.5970));
        droneRepository.save(new Drone("DRONE-N-02", "Medical Quadcopter - Class S", northZone, 88.0, 4.5, 40.0, 13.0450, 77.5920));
        droneRepository.save(new Drone("DRONE-N-03", "Medical Quadcopter - Class L", northZone, 92.0, 6.0, 50.0, 13.0312, 77.5649));

        // East Zone Drones
        droneRepository.save(new Drone("DRONE-E-01", "Medical Quadcopter - Class M", eastZone, 94.0, 5.0, 45.0, 12.9867, 77.7288));
        droneRepository.save(new Drone("DRONE-E-02", "Medical Quadcopter - Class S", eastZone, 85.0, 4.5, 40.0, 12.9698, 77.7500));
        droneRepository.save(new Drone("DRONE-E-03", "Medical Quadcopter - Class L", eastZone, 90.0, 6.0, 50.0, 12.9288, 77.6835));

        // South Zone Drones
        droneRepository.save(new Drone("DRONE-S-01", "Medical Quadcopter - Class M", southZone, 98.0, 5.0, 45.0, 12.8252, 77.6896));
        droneRepository.save(new Drone("DRONE-S-02", "Medical Quadcopter - Class S", southZone, 78.0, 4.5, 40.0, 12.8938, 77.5971));
        droneRepository.save(new Drone("DRONE-S-03", "Medical Quadcopter - Class L", southZone, 91.0, 6.0, 50.0, 12.9344, 77.6190));

        // West Zone Drones
        droneRepository.save(new Drone("DRONE-W-01", "Medical Quadcopter - Class M", westZone, 93.0, 5.0, 45.0, 12.9634, 77.5750));
        droneRepository.save(new Drone("DRONE-W-02", "Medical Quadcopter - Class S", westZone, 82.0, 4.5, 40.0, 12.9988, 77.5532));
        droneRepository.save(new Drone("DRONE-W-03", "Medical Quadcopter - Class L", westZone, 89.0, 6.0, 50.0, 13.0215, 77.5455));

        // Central Hub Reserve / Fallback Fleet (High payload & range)
        droneRepository.save(new Drone("DRONE-HUB-01", "Medical Quadcopter - Class XL", null, 100.0, 12.0, 75.0, 12.9750, 77.5950));
        droneRepository.save(new Drone("DRONE-HUB-02", "Medical Quadcopter - Class XL", null, 100.0, 12.0, 75.0, 12.9750, 77.5950));
    }

}
