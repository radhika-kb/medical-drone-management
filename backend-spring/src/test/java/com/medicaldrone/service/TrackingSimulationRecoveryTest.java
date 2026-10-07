package com.medicaldrone.service;

import com.medicaldrone.model.Drone;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.Mission;
import com.medicaldrone.model.Route;
import com.medicaldrone.model.RouteWaypoint;
import com.medicaldrone.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

class TrackingSimulationRecoveryTest {
    @Test
    void criticalAnomalyStopsSimulationBeforeDeliveryCompletion() {
        MissionRepository missions = mock(MissionRepository.class);
        RouteRepository routes = mock(RouteRepository.class);
        DroneRepository drones = mock(DroneRepository.class);
        MedicalRequestRepository requests = mock(MedicalRequestRepository.class);
        MissionTrackingRepository tracking = mock(MissionTrackingRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        AIServiceClient ai = mock(AIServiceClient.class);
        MissionMonitoringService recovery = mock(MissionMonitoringService.class);
        AuditService audit = mock(AuditService.class);

        Mission mission = new Mission();
        mission.setId(73L);
        mission.setMissionCode("MSN-73");
        mission.setStatus("DISPATCHED");
        mission.setDistanceKm(2.0);
        mission.setEtaMinutes(15.0);
        mission.setDrone(new Drone("DR-73", "Model", null, 95.0, 2.0, 20.0, 12.9, 77.5));
        mission.setRequest(new MedicalRequest());
        Route route = new Route(mission, "test", 2.0, 15.0, 15.0);
        route.addWaypoint(new RouteWaypoint(1, 12.9, 77.5, 60.0, "PICKUP"));
        route.addWaypoint(new RouteWaypoint(2, 12.91, 77.52, 60.0, "DESTINATION"));
        when(missions.findById(73L)).thenReturn(Optional.of(mission));
        when(routes.findByMissionId(73L)).thenReturn(Optional.of(route));
        when(ai.checkMissionMonitoring(anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Map.of("monitoring_available", true, "is_anomaly", true, "anomaly_score", 0.99,
                        "anomaly_type", "BATTERY_CRITICAL", "severity", "CRITICAL", "explanation", "reserve low"));
        when(recovery.recordAnomaly(anyLong(), anyDouble(), anyString(), anyString(), anyString())).thenReturn(true);
        TrackingSimulationService service = new TrackingSimulationService(missions, routes, drones, requests,
                tracking, notifications, messaging, ai, recovery, audit);

        service.startMissionSimulation(73L);

        assertNotEquals("DELIVERED", mission.getStatus());
        verify(recovery).recordAnomaly(73L, 0.99, "BATTERY_CRITICAL", "CRITICAL", "reserve low");
        verify(notifications, never()).save(any());
    }
}
