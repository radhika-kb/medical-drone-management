package com.medicaldrone.service;

import com.medicaldrone.dto.AIDecisionDto;
import com.medicaldrone.model.Drone;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.Mission;
import com.medicaldrone.model.Payment;
import com.medicaldrone.model.Route;
import com.medicaldrone.model.RouteWaypoint;
import com.medicaldrone.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServicePrerequisitesTest {

    @Mock private MissionRepository missionRepository;
    @Mock private MedicalRequestRepository requestRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DroneRepository droneRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private RouteWaypointRepository waypointRepository;
    @Mock private AgentDecisionRepository decisionRepository;
    @Mock private ETAPredictionRepository etaPredictionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private InventoryService inventoryService;
    @Mock private AIServiceClient aiServiceClient;
    @Mock private TrackingSimulationService simulationService;
    @Mock private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Mock private AuditService auditService;
    @Mock private WeatherObservationService weatherObservationService;

    private MissionService service;

    @BeforeEach
    void setUp() {
        service = new MissionService(missionRepository, requestRepository, paymentRepository,
                droneRepository, routeRepository, waypointRepository, decisionRepository,
                etaPredictionRepository, notificationRepository, inventoryService,
                aiServiceClient, simulationService, messagingTemplate, auditService,
                weatherObservationService);
    }

    @Test
    void cannotPrepareHubReviewBeforePaymentSucceeds() {
        MedicalRequest request = readyRequest();
        when(requestRepository.findById(31L)).thenReturn(Optional.of(request));
        when(paymentRepository.findByRequestId(31L)).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.prepareHubReview(31L, 7L, "admin@example.test"));

        assertTrue(error.getMessage().contains("Successful payment is required"));
        verifyNoInteractions(droneRepository, aiServiceClient, missionRepository);
    }

    @Test
    void cannotPrepareHubReviewWithoutAcceptedSupplierEvenAfterPayment() {
        MedicalRequest request = readyRequest();
        request.setSupplyingHospital(null);
        when(requestRepository.findById(31L)).thenReturn(Optional.of(request));
        when(paymentRepository.findByRequestId(31L)).thenReturn(List.of(successfulPayment(request)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.prepareHubReview(31L, 7L, "admin@example.test"));

        assertTrue(error.getMessage().contains("no accepted supplier"));
        verifyNoInteractions(droneRepository, aiServiceClient, missionRepository);
    }

    @Test
    void cannotPrepareHubReviewWhenAcceptedSupplierReservationIsMissing() {
        MedicalRequest request = readyRequest();
        request.setSupplyingHospital(supplier());
        when(requestRepository.findById(31L)).thenReturn(Optional.of(request));
        when(paymentRepository.findByRequestId(31L)).thenReturn(List.of(successfulPayment(request)));
        when(inventoryService.hasReservedStockForUpdate(12L, 8L, 2)).thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.prepareHubReview(31L, 7L, "admin@example.test"));

        assertTrue(error.getMessage().contains("required inventory reserved"));
        verifyNoInteractions(droneRepository, aiServiceClient, missionRepository);
    }

    @Test
    void centralHubCannotDispatchAnUnreviewedMission() {
        MedicalRequest request = readyRequest();
        Mission mission = new Mission();
        mission.setId(55L);
        mission.setRequest(request);
        mission.setStatus("READY_FOR_DISPATCH");
        when(missionRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(mission));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.applyHubDecision(55L, "DISPATCH", "go", 7L, "hub@example.test"));

        assertTrue(error.getMessage().contains("Only a reviewed or delayed mission"));
        verify(paymentRepository, never()).findByRequestId(any());
        verify(missionRepository, never()).save(any());
        verifyNoInteractions(simulationService);
    }

    @Test
    void reviewedMissionStillRequiresSuccessfulPaymentAtDispatchTime() {
        MedicalRequest request = readyRequest();
        request.setStatus("CENTRAL_HUB_REVIEW");
        Mission mission = new Mission();
        mission.setId(56L);
        mission.setRequest(request);
        mission.setStatus("CENTRAL_HUB_REVIEW");
        when(missionRepository.findByIdForUpdate(56L)).thenReturn(Optional.of(mission));
        when(paymentRepository.findByRequestId(31L)).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.applyHubDecision(56L, "DISPATCH", "approved", 7L, "hub@example.test"));

        assertTrue(error.getMessage().contains("Successful payment is required"));
        verify(droneRepository, never()).save(any());
        verifyNoInteractions(simulationService);
    }

    @Test
    void centralHubCannotDispatchIfInventoryReservationWasReleasedAfterReview() {
        MedicalRequest request = readyRequest();
        request.setStatus("CENTRAL_HUB_REVIEW");
        request.setSupplyingHospital(supplier());
        Mission mission = new Mission();
        mission.setId(57L);
        mission.setRequest(request);
        mission.setStatus("CENTRAL_HUB_REVIEW");
        when(missionRepository.findByIdForUpdate(57L)).thenReturn(Optional.of(mission));
        when(paymentRepository.findByRequestId(31L)).thenReturn(List.of(successfulPayment(request)));
        when(inventoryService.hasReservedStockForUpdate(12L, 8L, 2)).thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.applyHubDecision(57L, "DISPATCH", "approved", 7L, "hub@example.test"));

        assertTrue(error.getMessage().contains("required inventory reserved"));
        verify(missionRepository, never()).save(any());
        verifyNoInteractions(simulationService);
    }

    @Test
    void centralHubDispatchesAnApprovedReviewedMissionExactlyOnce() {
        MedicalRequest request = readyRequest();
        request.setStatus("CENTRAL_HUB_REVIEW");
        MedicalCenter supplier = supplier();
        MedicalCenter requester = new MedicalCenter();
        requester.setId(13L);
        requester.setName("Requester");
        requester.setLatitude(12.91);
        requester.setLongitude(77.52);
        request.setSupplyingHospital(supplier);
        request.setRequestingHospital(requester);
        Drone drone = new Drone("DR-1", "Medical-X", null, 95.0, 5.0, 40.0, 12.9, 77.5);
        drone.setId(3L);
        drone.setStatus("RESERVED_FOR_REVIEW");
        Mission mission = new Mission();
        mission.setId(58L);
        mission.setMissionCode("MSN-58");
        mission.setRequest(request);
        mission.setDrone(drone);
        mission.setSupplyingHospital(supplier);
        mission.setRequestingHospital(requester);
        mission.setStatus("CENTRAL_HUB_REVIEW");
        Route route = new Route(mission, "test-safe-route", 2.0, 10.0, 20.0);
        route.setId(6L);
        route.addWaypoint(new RouteWaypoint(1, 12.9, 77.5, 60.0, "PICKUP"));
        route.addWaypoint(new RouteWaypoint(2, 12.91, 77.52, 60.0, "DESTINATION"));
        when(missionRepository.findByIdForUpdate(58L)).thenReturn(Optional.of(mission));
        when(paymentRepository.findByRequestId(31L)).thenReturn(List.of(successfulPayment(request)));
        when(inventoryService.hasReservedStockForUpdate(12L, 8L, 2)).thenReturn(true);
        when(routeRepository.findByMissionId(58L)).thenReturn(Optional.of(route));

        service.applyHubDecision(58L, "DISPATCH", "human approval", 7L, "hub@example.test");

        assertEquals("DISPATCHED", mission.getStatus());
        assertEquals("DISPATCHED", request.getStatus());
        assertEquals("ASSIGNED", drone.getStatus());
        verify(auditService).record(7L, "hub@example.test", "MISSION_DISPATCHED", "missions", 58L, "drone=DR-1; reason=human approval");
        verify(notificationRepository).save(any());
        verify(messagingTemplate).convertAndSend(eq("/topic/missions"), org.mockito.ArgumentMatchers.<Object>any());
        verify(simulationService, times(1)).startMissionSimulation(58L);

        assertThrows(IllegalStateException.class,
                () -> service.applyHubDecision(58L, "DISPATCH", "duplicate", 7L, "hub@example.test"));
        verify(simulationService, times(1)).startMissionSimulation(58L);
    }

    @Test
    void springRejectsUnsafeAiRecommendationBeforePreparingHubMission() {
        MedicalRequest request = readyRequest();
        MedicalCenter supplier = new MedicalCenter();
        supplier.setId(12L);
        supplier.setName("Supplier");
        supplier.setLatitude(12.9);
        supplier.setLongitude(77.5);
        supplier.setVerificationStatus("APPROVED");
        request.setSupplyingHospital(supplier);
        when(requestRepository.findById(31L)).thenReturn(Optional.of(request));
        when(paymentRepository.findByRequestId(31L)).thenReturn(List.of(successfulPayment(request)));
        when(inventoryService.hasReservedStockForUpdate(12L, 8L, 2)).thenReturn(true);
        when(missionRepository.findByRequestId(31L)).thenReturn(Optional.empty());
        Drone drone = new Drone("DR-1", "Medical-X", null, 95.0, 5.0, 40.0, 12.9, 77.5);
        when(droneRepository.findByStatus("AVAILABLE")).thenReturn(List.of(drone));
        AIDecisionDto.ResponsePayload rejected = new AIDecisionDto.ResponsePayload();
        rejected.setStatus("REJECTED_WEATHER");
        rejected.setWeather(java.util.Map.of("flight_condition", "UNSAFE"));
        rejected.setSafety(java.util.Map.of("reason", "unsafe live weather"));
        when(aiServiceClient.getAIDecision(request, List.of(drone))).thenReturn(rejected);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.prepareHubReview(31L, 7L, "admin@example.test"));

        assertTrue(error.getMessage().contains("unsafe live weather"));
        verify(weatherObservationService).persistDecision(rejected.getWeather());
        verify(missionRepository, never()).saveAndFlush(any());
        verify(droneRepository, never()).save(any());
        verify(simulationService, never()).startMissionSimulation(any());
    }

    private MedicalRequest readyRequest() {
        MedicalRequest request = new MedicalRequest();
        request.setId(31L);
        request.setRequestCode("REQ-31");
        request.setStatus("READY_FOR_DISPATCH");
        request.setPickupLat(12.9);
        request.setPickupLng(77.5);
        request.setDestLat(12.91);
        request.setDestLng(77.52);
        request.setQuantity(2);
        com.medicaldrone.model.MedicalItem item = new com.medicaldrone.model.MedicalItem();
        item.setId(8L);
        item.setName("Test item");
        request.setMedicalItem(item);
        request.setWeightKg(1.0);
        return request;
    }

    private Payment successfulPayment(MedicalRequest request) {
        Payment payment = new Payment();
        payment.setPaymentStatus("SUCCESS");
        payment.setRequest(request);
        return payment;
    }

    private MedicalCenter supplier() {
        MedicalCenter supplier = new MedicalCenter();
        supplier.setId(12L);
        supplier.setName("Supplier");
        return supplier;
    }
}
