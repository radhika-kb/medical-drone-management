package com.medicaldrone.repository;

import com.medicaldrone.model.InventoryRiskAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InventoryRiskAlertRepository extends JpaRepository<InventoryRiskAlert, Long> {
    Optional<InventoryRiskAlert> findByHospitalIdAndMedicalItemId(Long hospitalId, Long medicalItemId);
    List<InventoryRiskAlert> findAllByOrderByLastEvaluatedAtDesc();
}
