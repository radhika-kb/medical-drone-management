package com.medicaldrone.repository;

import com.medicaldrone.model.HospitalInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalInventoryRepository extends JpaRepository<HospitalInventory, Long> {
    List<HospitalInventory> findByHospitalId(Long hospitalId);
    List<HospitalInventory> findByMedicalItemId(Long medicalItemId);
    Optional<HospitalInventory> findByHospitalIdAndMedicalItemId(Long hospitalId, Long medicalItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT hi FROM HospitalInventory hi WHERE hi.hospital.id = :hospitalId AND hi.medicalItem.id = :itemId")
    Optional<HospitalInventory> findByHospitalIdAndMedicalItemIdForUpdate(
            @Param("hospitalId") Long hospitalId, @Param("itemId") Long medicalItemId);

    @Query("SELECT hi FROM HospitalInventory hi WHERE hi.medicalItem.id = :itemId AND hi.availableQuantity >= :minQty AND hi.status IN ('AVAILABLE', 'LOW_STOCK') AND (hi.expiryDate IS NULL OR hi.expiryDate >= CURRENT_DATE) AND (hi.medicalItem.category NOT IN ('BLOOD', 'VACCINE', 'ANTIVENOM', 'EMERGENCY_MEDICINE') OR hi.expiryDate IS NOT NULL) AND hi.hospital.verificationStatus = 'APPROVED' AND hi.hospital.operationalStatus = 'ACTIVE' AND hi.hospital.status = 'ACTIVE'")
    List<HospitalInventory> findAvailableByItemAndQuantity(@Param("itemId") Long itemId, @Param("minQty") Integer minQty);
}
