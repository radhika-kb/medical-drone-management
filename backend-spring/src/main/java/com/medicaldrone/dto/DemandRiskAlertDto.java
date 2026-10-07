package com.medicaldrone.dto;

import java.time.LocalDateTime;

public record DemandRiskAlertDto(
        Long id,
        Long hospitalId,
        String hospitalName,
        Long zoneId,
        String zoneName,
        Long medicalItemId,
        String medicalItemCode,
        String medicalItemName,
        String unit,
        String predictionPeriod,
        Integer predictedDemand,
        Integer availableQuantity,
        Integer reservedQuantity,
        Integer shortageQuantity,
        String riskLevel,
        String status,
        LocalDateTime lastEvaluatedAt
) {}
