package com.medicaldrone.dto;

import java.time.LocalDateTime;

public record DemandForecastRunDto(
        String status,
        int hospitalsEvaluated,
        int medicalItemsEvaluated,
        int forecastsCreated,
        int highRiskCount,
        int mediumRiskCount,
        int lowRiskCount,
        int skippedFacilities,
        LocalDateTime completedAt
) {}
