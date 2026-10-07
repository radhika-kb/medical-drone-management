package com.medicaldrone.repository;

public interface DemandHistoryProjection {
    Long getPreviousDayDemand();
    Long getPreviousWeekDemand();
    Long getEmergencyRequests();
}
