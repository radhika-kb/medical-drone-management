package com.medicaldrone.service;

import com.medicaldrone.dto.DemandForecastRunDto;
import com.medicaldrone.dto.DemandRiskAlertDto;
import com.medicaldrone.model.*;
import com.medicaldrone.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DemandForecastService {
    private static final Logger logger = LoggerFactory.getLogger(DemandForecastService.class);
    private final MedicalCenterRepository hospitalRepository;
    private final MedicalItemRepository itemRepository;
    private final HospitalInventoryRepository inventoryRepository;
    private final MedicalRequestRepository requestRepository;
    private final DemandPredictionRepository predictionRepository;
    private final InventoryRiskAlertRepository riskAlertRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final AIServiceClient aiServiceClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DemandForecastService(MedicalCenterRepository hospitalRepository,
                                 MedicalItemRepository itemRepository,
                                 HospitalInventoryRepository inventoryRepository,
                                 MedicalRequestRepository requestRepository,
                                 DemandPredictionRepository predictionRepository,
                                 InventoryRiskAlertRepository riskAlertRepository,
                                 UserRepository userRepository,
                                 NotificationRepository notificationRepository,
                                 AIServiceClient aiServiceClient) {
        this.hospitalRepository = hospitalRepository;
        this.itemRepository = itemRepository;
        this.inventoryRepository = inventoryRepository;
        this.requestRepository = requestRepository;
        this.predictionRepository = predictionRepository;
        this.riskAlertRepository = riskAlertRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.aiServiceClient = aiServiceClient;
    }

    @Scheduled(cron = "${demand.forecast.cron:0 0 */6 * * *}")
    public void scheduledForecast() {
        try {
            runForecasts();
        } catch (Exception ex) {
            logger.error("Scheduled demand and inventory-risk forecast failed", ex);
        }
    }

    public DemandForecastRunDto runForecasts() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A demand forecast run is already in progress");
        }
        try {
            List<MedicalCenter> hospitals = hospitalRepository
                    .findByVerificationStatusAndOperationalStatusAndStatus("APPROVED", "ACTIVE", "ACTIVE");
            List<MedicalItem> items = itemRepository.findByActiveTrue();
            Map<String, HospitalInventory> stockByPair = new HashMap<>();
            for (HospitalInventory inventory : inventoryRepository.findAll()) {
                stockByPair.put(pairKey(inventory.getHospital().getId(), inventory.getMedicalItem().getId()), inventory);
            }

            int forecasts = 0, high = 0, medium = 0, low = 0, skipped = 0;
            for (MedicalCenter hospital : hospitals) {
                if (hospital.getZone() == null) {
                    skipped++;
                    continue;
                }
                for (MedicalItem item : items) {
                    HospitalInventory inventory = stockByPair.get(pairKey(hospital.getId(), item.getId()));
                    DemandRiskAlertDto outcome = evaluate(hospital, item, inventory);
                    forecasts++;
                    switch (outcome.riskLevel()) {
                        case "HIGH" -> high++;
                        case "MEDIUM" -> medium++;
                        default -> low++;
                    }
                }
            }
            return new DemandForecastRunDto("COMPLETED", hospitals.size(), items.size(), forecasts,
                    high, medium, low, skipped, LocalDateTime.now());
        } finally {
            running.set(false);
        }
    }

    public DemandRiskAlertDto forecastOne(Long hospitalId, Long medicalItemId) {
        MedicalCenter hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found"));
        if (!"APPROVED".equalsIgnoreCase(hospital.getVerificationStatus())
                || !"ACTIVE".equalsIgnoreCase(hospital.getOperationalStatus())
                || !"ACTIVE".equalsIgnoreCase(hospital.getStatus())) {
            throw new IllegalStateException("Only approved, active hospitals can be forecast");
        }
        if (hospital.getZone() == null) throw new IllegalStateException("Hospital must be assigned to a zone");
        MedicalItem item = itemRepository.findById(medicalItemId)
                .filter(MedicalItem::getActive)
                .orElseThrow(() -> new IllegalArgumentException("Active medical item not found"));
        HospitalInventory inventory = inventoryRepository
                .findByHospitalIdAndMedicalItemId(hospitalId, medicalItemId).orElse(null);
        return evaluate(hospital, item, inventory);
    }

    public List<DemandRiskAlertDto> getActiveRiskAlerts() {
        return riskAlertRepository.findAllByOrderByLastEvaluatedAtDesc().stream()
                .map(this::toDto).toList();
    }

    private DemandRiskAlertDto evaluate(MedicalCenter hospital, MedicalItem item, HospitalInventory inventory) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.minusDays(1);
        LocalDateTime weekStart = now.minusDays(7);
        DemandHistoryProjection history = requestRepository.getRecentDemandHistory(
                hospital.getId(), item.getId(), dayStart, weekStart);
        long previousDay = history == null || history.getPreviousDayDemand() == null ? 0 : history.getPreviousDayDemand();
        long previousWeek = history == null || history.getPreviousWeekDemand() == null ? 0 : history.getPreviousWeekDemand();
        long emergencyRequests = history == null || history.getEmergencyRequests() == null ? 0 : history.getEmergencyRequests();

        int available = inventory == null ? 0 : zero(inventory.getAvailableQuantity());
        int reserved = inventory == null ? 0 : zero(inventory.getReservedQuantity());
        if (inventory != null && inventory.getExpiryDate() != null && inventory.getExpiryDate().isBefore(LocalDate.now())) {
            available = 0;
            reserved = 0;
        }

        Map<String, Object> prediction = aiServiceClient.forecastDemand(hospital.getId(), item.getId(),
                item.getCategory(), available, previousDay, previousWeek, emergencyRequests);
        int predicted = Math.max(0, (int) Math.ceil(number(prediction.get("predicted_demand_quantity"), 0.0)));
        int shortage = Math.max(0, predicted - available);
        String risk = classifyRisk(predicted, shortage);

        DemandPrediction savedPrediction = new DemandPrediction(item, hospital.getZone(), "NEXT_24H",
                (double) predicted, Objects.toString(prediction.get("model_name"), "DemandForecast"), "1.0.0");
        savedPrediction.setHospital(hospital);
        predictionRepository.save(savedPrediction);

        InventoryRiskAlert alert = riskAlertRepository.findByHospitalIdAndMedicalItemId(hospital.getId(), item.getId())
                .orElseGet(InventoryRiskAlert::new);
        String oldStatus = alert.getId() == null ? null : alert.getStatus();
        String oldRisk = alert.getId() == null ? null : alert.getRiskLevel();
        int oldShortage = alert.getId() == null ? 0 : zero(alert.getShortageQuantity());
        alert.setHospital(hospital);
        alert.setMedicalItem(item);
        alert.setZone(hospital.getZone());
        alert.setPredictionPeriod("NEXT_24H");
        alert.setPredictedDemand(predicted);
        alert.setAvailableQuantity(available);
        alert.setReservedQuantity(reserved);
        alert.setShortageQuantity(shortage);
        alert.setRiskLevel(risk);
        alert.setLastEvaluatedAt(now);

        if ("LOW".equals(risk)) {
            boolean resolved = "ACTIVE".equals(oldStatus);
            alert.setStatus("RESOLVED");
            if (resolved || alert.getResolvedAt() == null) alert.setResolvedAt(now);
            riskAlertRepository.save(alert);
            if (resolved) notifyAdmins("INVENTORY_RISK_RESOLVED", alert,
                    "Inventory risk cleared for " + item.getName() + " at " + hospital.getName() + ".");
        } else {
            alert.setStatus("ACTIVE");
            alert.setResolvedAt(null);
            riskAlertRepository.save(alert);
            boolean newAlert = !"ACTIVE".equals(oldStatus);
            boolean escalated = riskRank(risk) > riskRank(oldRisk);
            boolean materialShortageIncrease = shortage >= oldShortage + Math.max(5, (int) Math.ceil(oldShortage * 0.25));
            if (newAlert || escalated || materialShortageIncrease) {
                notifyAdmins("INVENTORY_RISK_ALERT", alert, alertMessage(alert));
            }
        }
        return toDto(alert);
    }

    private void notifyAdmins(String type, InventoryRiskAlert alert, String message) {
        userRepository.findByRoleNameAndStatus(Role.ADMIN, "ACTIVE")
                .forEach(admin -> notificationRepository.save(new Notification(admin, null, type, message)));
    }

    private String alertMessage(InventoryRiskAlert alert) {
        return String.format(Locale.ROOT,
                "%s inventory risk at %s (%s): next 24h demand %d %s, available %d, reserved %d, shortage %d.",
                alert.getRiskLevel(), alert.getHospital().getName(), alert.getZone().getName(),
                alert.getPredictedDemand(), alert.getMedicalItem().getUnit(), alert.getAvailableQuantity(),
                alert.getReservedQuantity(), alert.getShortageQuantity());
    }

    private DemandRiskAlertDto toDto(InventoryRiskAlert alert) {
        MedicalItem item = alert.getMedicalItem();
        return new DemandRiskAlertDto(alert.getId(), alert.getHospital().getId(), alert.getHospital().getName(),
                alert.getZone().getId(), alert.getZone().getName(), item.getId(), item.getCode(), item.getName(),
                item.getUnit(), alert.getPredictionPeriod(), alert.getPredictedDemand(),
                alert.getAvailableQuantity(), alert.getReservedQuantity(), alert.getShortageQuantity(),
                alert.getRiskLevel(), alert.getStatus(), alert.getLastEvaluatedAt());
    }

    private String classifyRisk(int predicted, int shortage) {
        if (shortage <= 0) return "LOW";
        int highThreshold = Math.max(10, (int) Math.ceil(predicted * 0.25));
        return shortage >= highThreshold ? "HIGH" : "MEDIUM";
    }

    private int riskRank(String risk) {
        if ("HIGH".equals(risk)) return 2;
        if ("MEDIUM".equals(risk)) return 1;
        return 0;
    }

    private int zero(Integer value) { return value == null ? 0 : Math.max(0, value); }
    private double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
    private String pairKey(Long hospitalId, Long itemId) { return hospitalId + ":" + itemId; }
}
