package com.medicaldrone.repository;

import com.medicaldrone.model.MedicalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface MedicalRequestRepository extends JpaRepository<MedicalRequest, Long> {
    Optional<MedicalRequest> findByRequestCode(String requestCode);
    List<MedicalRequest> findByStatus(String status);
    List<MedicalRequest> findByZoneId(Long zoneId);
    @org.springframework.data.jpa.repository.Query("SELECT r FROM MedicalRequest r WHERE r.requestingHospital.id = :centerId OR r.supplyingHospital.id = :centerId")
    List<MedicalRequest> findByCenterId(@org.springframework.data.repository.query.Param("centerId") Long centerId);
    List<MedicalRequest> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query("""
            SELECT COALESCE(SUM(CASE WHEN r.createdAt >= :dayStart THEN r.quantity ELSE 0 END), 0) AS previousDayDemand,
                   COALESCE(SUM(r.quantity), 0) AS previousWeekDemand,
                   COALESCE(SUM(CASE WHEN r.urgency IN ('CRITICAL', 'HIGH') THEN 1 ELSE 0 END), 0) AS emergencyRequests
            FROM MedicalRequest r
            WHERE r.requestingHospital.id = :hospitalId
              AND r.medicalItem.id = :medicalItemId
              AND r.createdAt >= :weekStart
              AND r.status NOT IN ('DRAFT', 'CANCELLED')
            """)
    DemandHistoryProjection getRecentDemandHistory(
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
            @org.springframework.data.repository.query.Param("medicalItemId") Long medicalItemId,
            @org.springframework.data.repository.query.Param("dayStart") LocalDateTime dayStart,
            @org.springframework.data.repository.query.Param("weekStart") LocalDateTime weekStart);
}
