package com.medicaldrone.dto;

import java.time.LocalDateTime;

public record MissionAnomalyDto(Long id, Long missionId, String missionCode, String anomalyType,
                                String severity, Double anomalyScore, String explanation,
                                LocalDateTime detectedAt, String recommendedAction) {}
