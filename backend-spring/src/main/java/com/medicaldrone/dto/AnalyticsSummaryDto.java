package com.medicaldrone.dto;

import java.util.Map;

public class AnalyticsSummaryDto {
    private long totalRequests;
    private long pendingRequests;
    private long totalMissions;
    private long activeMissions;
    private long deliveredMissions;
    private long totalDrones;
    private long availableDrones;
    private double avgBatteryPct;
    private Map<String, Long> dronesPerZone;
    private Map<String, Long> missionsPerZone;

    public AnalyticsSummaryDto() {}

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
    public long getPendingRequests() { return pendingRequests; }
    public void setPendingRequests(long pendingRequests) { this.pendingRequests = pendingRequests; }
    public long getTotalMissions() { return totalMissions; }
    public void setTotalMissions(long totalMissions) { this.totalMissions = totalMissions; }
    public long getActiveMissions() { return activeMissions; }
    public void setActiveMissions(long activeMissions) { this.activeMissions = activeMissions; }
    public long getDeliveredMissions() { return deliveredMissions; }
    public void setDeliveredMissions(long deliveredMissions) { this.deliveredMissions = deliveredMissions; }
    public long getTotalDrones() { return totalDrones; }
    public void setTotalDrones(long totalDrones) { this.totalDrones = totalDrones; }
    public long getAvailableDrones() { return availableDrones; }
    public void setAvailableDrones(long availableDrones) { this.availableDrones = availableDrones; }
    public double getAvgBatteryPct() { return avgBatteryPct; }
    public void setAvgBatteryPct(double avgBatteryPct) { this.avgBatteryPct = avgBatteryPct; }
    public Map<String, Long> getDronesPerZone() { return dronesPerZone; }
    public void setDronesPerZone(Map<String, Long> dronesPerZone) { this.dronesPerZone = dronesPerZone; }
    public Map<String, Long> getMissionsPerZone() { return missionsPerZone; }
    public void setMissionsPerZone(Map<String, Long> missionsPerZone) { this.missionsPerZone = missionsPerZone; }
}
