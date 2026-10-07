package com.medicaldrone.dto;

import java.time.LocalDateTime;
import java.util.List;

public class MissionDto {

    public static class Response {
        private Long id;
        private String missionCode;
        private Long requestId;
        private String requestCode;
        private String medicalType;
        private String urgency;
        private Long requestingHospitalId;
        private String requestingHospitalName;
        private Long supplyingHospitalId;
        private String supplyingHospitalName;
        private Long droneId;
        private String droneCode;
        private String droneModel;
        private String status;
        private String hubDecisionReason;
        private Double distanceKm;
        private Double etaMinutes;
        private Double baselineEtaMinutes;
        private Double mlEtaMinutes;
        private Double finalEtaMinutes;
        private Double batteryPct;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private List<WaypointDto> waypoints;
        private List<DecisionTrailDto> decisionTrail;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getMissionCode() { return missionCode; }
        public void setMissionCode(String missionCode) { this.missionCode = missionCode; }
        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
        public String getRequestCode() { return requestCode; }
        public void setRequestCode(String requestCode) { this.requestCode = requestCode; }
        public String getMedicalType() { return medicalType; }
        public void setMedicalType(String medicalType) { this.medicalType = medicalType; }
        public String getUrgency() { return urgency; }
        public void setUrgency(String urgency) { this.urgency = urgency; }
        public Long getRequestingHospitalId() { return requestingHospitalId; }
        public void setRequestingHospitalId(Long requestingHospitalId) { this.requestingHospitalId = requestingHospitalId; }
        public String getRequestingHospitalName() { return requestingHospitalName; }
        public void setRequestingHospitalName(String requestingHospitalName) { this.requestingHospitalName = requestingHospitalName; }
        public Long getSupplyingHospitalId() { return supplyingHospitalId; }
        public void setSupplyingHospitalId(Long supplyingHospitalId) { this.supplyingHospitalId = supplyingHospitalId; }
        public String getSupplyingHospitalName() { return supplyingHospitalName; }
        public void setSupplyingHospitalName(String supplyingHospitalName) { this.supplyingHospitalName = supplyingHospitalName; }
        public Long getDroneId() { return droneId; }
        public void setDroneId(Long droneId) { this.droneId = droneId; }
        public String getDroneCode() { return droneCode; }
        public void setDroneCode(String droneCode) { this.droneCode = droneCode; }
        public String getDroneModel() { return droneModel; }
        public void setDroneModel(String droneModel) { this.droneModel = droneModel; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getHubDecisionReason() { return hubDecisionReason; }
        public void setHubDecisionReason(String hubDecisionReason) { this.hubDecisionReason = hubDecisionReason; }
        public Double getDistanceKm() { return distanceKm; }
        public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
        public Double getEtaMinutes() { return etaMinutes; }
        public void setEtaMinutes(Double etaMinutes) { this.etaMinutes = etaMinutes; }
        public Double getBaselineEtaMinutes() { return baselineEtaMinutes; }
        public void setBaselineEtaMinutes(Double baselineEtaMinutes) { this.baselineEtaMinutes = baselineEtaMinutes; }
        public Double getMlEtaMinutes() { return mlEtaMinutes; }
        public void setMlEtaMinutes(Double mlEtaMinutes) { this.mlEtaMinutes = mlEtaMinutes; }
        public Double getFinalEtaMinutes() { return finalEtaMinutes; }
        public void setFinalEtaMinutes(Double finalEtaMinutes) { this.finalEtaMinutes = finalEtaMinutes; }
        public Double getBatteryPct() { return batteryPct; }
        public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
        public List<WaypointDto> getWaypoints() { return waypoints; }
        public void setWaypoints(List<WaypointDto> waypoints) { this.waypoints = waypoints; }
        public List<DecisionTrailDto> getDecisionTrail() { return decisionTrail; }
        public void setDecisionTrail(List<DecisionTrailDto> decisionTrail) { this.decisionTrail = decisionTrail; }
    }

    public static class WaypointDto {
        private Integer sequenceNo;
        private Double lat;
        private Double lng;
        private Double altitudeMeters;
        private String type;

        public WaypointDto() {}

        public WaypointDto(Integer sequenceNo, Double lat, Double lng, Double altitudeMeters, String type) {
            this.sequenceNo = sequenceNo;
            this.lat = lat;
            this.lng = lng;
            this.altitudeMeters = altitudeMeters;
            this.type = type;
        }

        public Integer getSequenceNo() { return sequenceNo; }
        public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }
        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
        public Double getAltitudeMeters() { return altitudeMeters; }
        public void setAltitudeMeters(Double altitudeMeters) { this.altitudeMeters = altitudeMeters; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class DecisionTrailDto {
        private String agent;
        private String decision;
        private Double score;
        private String tier;
        private String reason;

        public DecisionTrailDto() {}

        public DecisionTrailDto(String agent, String decision, Double score, String tier, String reason) {
            this.agent = agent;
            this.decision = decision;
            this.score = score;
            this.tier = tier;
            this.reason = reason;
        }

        public String getAgent() { return agent; }
        public void setAgent(String agent) { this.agent = agent; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class TelemetryUpdate {
        private Long missionId;
        private String missionCode;
        private String droneCode;
        private Double lat;
        private Double lng;
        private Double batteryPct;
        private Double etaMinutes;
        private Double remainingDistanceKm;
        private String status;

        public TelemetryUpdate() {}

        public Long getMissionId() { return missionId; }
        public void setMissionId(Long missionId) { this.missionId = missionId; }
        public String getMissionCode() { return missionCode; }
        public void setMissionCode(String missionCode) { this.missionCode = missionCode; }
        public String getDroneCode() { return droneCode; }
        public void setDroneCode(String droneCode) { this.droneCode = droneCode; }
        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
        public Double getBatteryPct() { return batteryPct; }
        public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }
        public Double getEtaMinutes() { return etaMinutes; }
        public void setEtaMinutes(Double etaMinutes) { this.etaMinutes = etaMinutes; }
        public Double getRemainingDistanceKm() { return remainingDistanceKm; }
        public void setRemainingDistanceKm(Double remainingDistanceKm) { this.remainingDistanceKm = remainingDistanceKm; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
