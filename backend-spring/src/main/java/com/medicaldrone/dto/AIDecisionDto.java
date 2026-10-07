package com.medicaldrone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class AIDecisionDto {

    public static class RequestPayload {
        @JsonProperty("request_id")
        private Long requestId;
        @JsonProperty("request_code")
        private String requestCode;
        @JsonProperty("medical_type")
        private String medicalType;
        private String urgency;
        @JsonProperty("weight_kg")
        private Double weightKg;
        private GeoPoint pickup;
        private GeoPoint destination;
        @JsonProperty("preferred_zone")
        private String preferredZone;
        private List<DronePayload> drones;
        @JsonProperty("no_fly_zones")
        private List<NoFlyZonePayload> noFlyZones;
        @JsonProperty("required_quantity")
        private Integer requiredQuantity;
        @JsonProperty("supplier_accepted")
        private Boolean supplierAccepted;
        @JsonProperty("supplier_candidates")
        private List<Map<String, Object>> supplierCandidates;

        public RequestPayload() {}

        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
        public String getRequestCode() { return requestCode; }
        public void setRequestCode(String requestCode) { this.requestCode = requestCode; }
        public String getMedicalType() { return medicalType; }
        public void setMedicalType(String medicalType) { this.medicalType = medicalType; }
        public String getUrgency() { return urgency; }
        public void setUrgency(String urgency) { this.urgency = urgency; }
        public Double getWeightKg() { return weightKg; }
        public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
        public GeoPoint getPickup() { return pickup; }
        public void setPickup(GeoPoint pickup) { this.pickup = pickup; }
        public GeoPoint getDestination() { return destination; }
        public void setDestination(GeoPoint destination) { this.destination = destination; }
        public String getPreferredZone() { return preferredZone; }
        public void setPreferredZone(String preferredZone) { this.preferredZone = preferredZone; }
        public List<DronePayload> getDrones() { return drones; }
        public void setDrones(List<DronePayload> drones) { this.drones = drones; }
        public List<NoFlyZonePayload> getNoFlyZones() { return noFlyZones; }
        public void setNoFlyZones(List<NoFlyZonePayload> noFlyZones) { this.noFlyZones = noFlyZones; }
        public Integer getRequiredQuantity() { return requiredQuantity; }
        public void setRequiredQuantity(Integer requiredQuantity) { this.requiredQuantity = requiredQuantity; }
        public Boolean getSupplierAccepted() { return supplierAccepted; }
        public void setSupplierAccepted(Boolean supplierAccepted) { this.supplierAccepted = supplierAccepted; }
        public List<Map<String, Object>> getSupplierCandidates() { return supplierCandidates; }
        public void setSupplierCandidates(List<Map<String, Object>> supplierCandidates) { this.supplierCandidates = supplierCandidates; }
    }

    public static class GeoPoint {
        private Double lat;
        private Double lng;

        public GeoPoint() {}
        public GeoPoint(Double lat, Double lng) { this.lat = lat; this.lng = lng; }

        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
    }

    public static class DronePayload {
        private Long id;
        @JsonProperty("drone_code")
        private String droneCode;
        private String model;
        @JsonProperty("zone_name")
        private String zoneName;
        @JsonProperty("battery_pct")
        private Double batteryPct;
        @JsonProperty("max_payload_kg")
        private Double maxPayloadKg;
        @JsonProperty("max_range_km")
        private Double maxRangeKm;
        private Double latitude;
        private Double longitude;
        private String status;

        public DronePayload() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDroneCode() { return droneCode; }
        public void setDroneCode(String droneCode) { this.droneCode = droneCode; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getZoneName() { return zoneName; }
        public void setZoneName(String zoneName) { this.zoneName = zoneName; }
        public Double getBatteryPct() { return batteryPct; }
        public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }
        public Double getMaxPayloadKg() { return maxPayloadKg; }
        public void setMaxPayloadKg(Double maxPayloadKg) { this.maxPayloadKg = maxPayloadKg; }
        public Double getMaxRangeKm() { return maxRangeKm; }
        public void setMaxRangeKm(Double maxRangeKm) { this.maxRangeKm = maxRangeKm; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class NoFlyZonePayload {
        private String name;
        private String type;
        @JsonProperty("center_lat")
        private Double centerLat;
        @JsonProperty("center_lng")
        private Double centerLng;
        @JsonProperty("radius_km")
        private Double radiusKm;

        public NoFlyZonePayload() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Double getCenterLat() { return centerLat; }
        public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }
        public Double getCenterLng() { return centerLng; }
        public void setCenterLng(Double centerLng) { this.centerLng = centerLng; }
        public Double getRadiusKm() { return radiusKm; }
        public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }
    }

    public static class ResponsePayload {
        private String status;
        @JsonProperty("request_code")
        private String requestCode;
        private Map<String, Object> priority;
        private Map<String, Object> weather;
        private Map<String, Object> allocation;
        private Map<String, Object> route;
        private Map<String, Object> safety;
        @JsonProperty("decision_trail")
        private List<Map<String, Object>> decisionTrail;

        public ResponsePayload() {}

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRequestCode() { return requestCode; }
        public void setRequestCode(String requestCode) { this.requestCode = requestCode; }
        public Map<String, Object> getPriority() { return priority; }
        public void setPriority(Map<String, Object> priority) { this.priority = priority; }
        public Map<String, Object> getWeather() { return weather; }
        public void setWeather(Map<String, Object> weather) { this.weather = weather; }
        public Map<String, Object> getAllocation() { return allocation; }
        public void setAllocation(Map<String, Object> allocation) { this.allocation = allocation; }
        public Map<String, Object> getRoute() { return route; }
        public void setRoute(Map<String, Object> route) { this.route = route; }
        public Map<String, Object> getSafety() { return safety; }
        public void setSafety(Map<String, Object> safety) { this.safety = safety; }
        public List<Map<String, Object>> getDecisionTrail() { return decisionTrail; }
        public void setDecisionTrail(List<Map<String, Object>> decisionTrail) { this.decisionTrail = decisionTrail; }
    }
}
