package com.medicaldrone.service;

import com.medicaldrone.model.Drone;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.model.MedicalItem;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.Mission;
import com.medicaldrone.model.Route;
import com.medicaldrone.model.RouteWaypoint;
import com.medicaldrone.dto.AIDecisionDto;
import com.medicaldrone.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

class MissionMonitoringRecoveryTest {
    @Test
    void anomalyPersistsAndMovesMissionAndRequestIntoHubRecoveryHold() {
        MissionRepository missions = mock(MissionRepository.class);
        MissionAnomalyRepository anomalies = mock(MissionAnomalyRepository.class);
        MedicalRequestRepository requests = mock(MedicalRequestRepository.class);
        DroneRepository drones = mock(DroneRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        AuditService audit = mock(AuditService.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        Mission mission = new Mission();
        mission.setId(51L);
        mission.setMissionCode("MSN-51");
        mission.setStatus("IN_TRANSIT");
        mission.setRequest(new MedicalRequest());
        mission.getRequest().setId(31L);
        mission.setDrone(new Drone());
        when(missions.findByIdForUpdate(51L)).thenReturn(Optional.of(mission));
        when(anomalies.save(any())).thenAnswer(invocation -> {
            var saved = (com.medicaldrone.model.MissionAnomaly) invocation.getArgument(0);
            saved.setId(91L);
            return saved;
        });

        MissionMonitoringService service = new MissionMonitoringService(missions, anomalies, requests,
                drones, notifications, audit, messaging);
        boolean held = service.recordAnomaly(51L, 0.97, "BATTERY_CRITICAL", "CRITICAL", "Battery reserve unsafe");

        assertTrue(held);
        assertEquals("RECOVERY_REQUIRED", mission.getStatus());
        assertEquals("RECOVERY_REQUIRED", mission.getRequest().getStatus());
        assertEquals("RECOVERY_REQUIRED", mission.getDrone().getStatus());
        verify(anomalies).save(any());
        verify(audit).record(isNull(), eq("MISSION_MONITOR"), eq("MISSION_RECOVERY_REQUIRED"),
                eq("missions"), eq(51L), contains("BATTERY_CRITICAL"));
        var alertPayload = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(messaging).convertAndSend(eq("/topic/alerts"), alertPayload.capture());
        assertEquals("RECOVERY_REQUIRED", alertPayload.getValue().get("missionStatus"));
    }

    @Test
    void staleOrNonAirborneAnomalyCannotMoveMissionIntoRecoveryTwice() {
        MissionRepository missions = mock(MissionRepository.class);
        Mission mission = new Mission();
        mission.setId(52L);
        mission.setStatus("DELIVERED");
        when(missions.findByIdForUpdate(52L)).thenReturn(Optional.of(mission));
        MissionMonitoringService service = new MissionMonitoringService(missions,
                mock(MissionAnomalyRepository.class), mock(MedicalRequestRepository.class),
                mock(DroneRepository.class), mock(NotificationRepository.class), mock(AuditService.class),
                mock(SimpMessagingTemplate.class));

        assertFalse(service.recordAnomaly(52L, 0.9, "DELAY", "HIGH", "late"));
        verify(missions, never()).save(any());
    }

    @Test
    void hubReassignmentFromRecoveryRecomputesRouteAndKeepsExistingInventoryReservation() {
        MissionRepository missions = mock(MissionRepository.class);
        MedicalRequestRepository requests = mock(MedicalRequestRepository.class);
        DroneRepository drones = mock(DroneRepository.class);
        RouteRepository routes = mock(RouteRepository.class);
        RouteWaypointRepository waypoints = mock(RouteWaypointRepository.class);
        AgentDecisionRepository decisions = mock(AgentDecisionRepository.class);
        ETAPredictionRepository eta = mock(ETAPredictionRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        AIServiceClient ai = mock(AIServiceClient.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        Mission mission = new Mission();
        mission.setId(61L);
        mission.setMissionCode("MSN-61");
        mission.setStatus("RECOVERY_REQUIRED");
        Drone prior = new Drone("DR-OLD", "Model", null, 50.0, 2.0, 20.0, 12.9, 77.5);
        prior.setId(3L);
        prior.setStatus("RECOVERY_REQUIRED");
        Drone replacement = new Drone("DR-NEW", "Model", null, 95.0, 2.0, 20.0, 12.9, 77.5);
        replacement.setId(4L);
        mission.setDrone(prior);
        MedicalRequest request = new MedicalRequest();
        request.setId(41L);
        request.setRequestCode("REQ-41");
        request.setStatus("RECOVERY_REQUIRED");
        request.setPickupLat(12.9);
        request.setPickupLng(77.5);
        request.setDestLat(12.91);
        request.setDestLng(77.52);
        request.setQuantity(2);
        MedicalItem item = new MedicalItem();
        item.setId(8L);
        request.setMedicalItem(item);
        mission.setRequest(request);
        MedicalCenter supplier = new MedicalCenter();
        supplier.setId(12L);
        mission.setSupplyingHospital(supplier);
        mission.setRequestingHospital(new MedicalCenter());
        Route route = new Route(mission, "old-route", 3.0, 10.0, 12.0);
        route.addWaypoint(new RouteWaypoint(1, 12.9, 77.5, 60.0, "PICKUP"));
        when(missions.findByIdForUpdate(61L)).thenReturn(Optional.of(mission));
        when(drones.findByStatus("AVAILABLE")).thenReturn(java.util.List.of(replacement));
        when(drones.findByIdWithLock(4L)).thenReturn(Optional.of(replacement));
        when(routes.findByMissionId(61L)).thenReturn(Optional.of(route));
        when(decisions.findByMissionIdOrderByCreatedAtAsc(61L)).thenReturn(java.util.List.of());
        AIDecisionDto.ResponsePayload advisor = new AIDecisionDto.ResponsePayload();
        advisor.setStatus("APPROVED");
        advisor.setAllocation(java.util.Map.of("drone_id", 4));
        advisor.setRoute(java.util.Map.of("algorithm", "test-safe-route", "distance_km", 3.0,
                "baseline_eta_minutes", 10.0, "ml_predicted_eta_minutes", 12.0, "final_eta_minutes", 11.0,
                "waypoints", java.util.List.of(java.util.Map.of("lat", 12.9, "lng", 77.5, "type", "PICKUP"))));
        when(ai.getAIDecision(eq(request), anyList())).thenReturn(advisor);
        MissionService service = new MissionService(missions, requests, mock(PaymentRepository.class), drones,
                routes, waypoints, decisions, eta, notifications, inventory, ai, mock(TrackingSimulationService.class),
                messaging, mock(AuditService.class), mock(WeatherObservationService.class));

        service.reassignMission(61L, "battery anomaly", 5L, "hub@example.test");

        assertEquals("MAINTENANCE", prior.getStatus());
        assertEquals("RESERVED_FOR_REVIEW", replacement.getStatus());
        assertEquals(replacement, mission.getDrone());
        assertEquals("CENTRAL_HUB_REVIEW", mission.getStatus());
        assertEquals("CENTRAL_HUB_REVIEW", request.getStatus());
        verify(routes).saveAndFlush(route);
        verify(waypoints).save(any());
        verify(inventory, never()).releaseReservedStock(anyLong(), anyLong(), anyInt());
        verify(inventory, never()).reserveStock(anyLong(), anyLong(), anyInt());
    }
}
