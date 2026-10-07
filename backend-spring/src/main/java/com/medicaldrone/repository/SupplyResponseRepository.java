package com.medicaldrone.repository;

import com.medicaldrone.model.SupplyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplyResponseRepository extends JpaRepository<SupplyResponse, Long> {
    List<SupplyResponse> findByRequestId(Long requestId);
    List<SupplyResponse> findByRequestIdOrderByIdAsc(Long requestId);
    List<SupplyResponse> findByRequestIdAndResponseOrderByIdAsc(Long requestId, String response);
    List<SupplyResponse> findBySupplyingHospitalIdAndResponseOrderByCreatedAtAsc(Long supplyingHospitalId, String response);
    List<SupplyResponse> findBySupplyingHospitalId(Long supplyingHospitalId);
    Optional<SupplyResponse> findByRequestIdAndSupplyingHospitalId(Long requestId, Long supplyingHospitalId);
}
