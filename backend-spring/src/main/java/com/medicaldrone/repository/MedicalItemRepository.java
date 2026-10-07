package com.medicaldrone.repository;

import com.medicaldrone.model.MedicalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalItemRepository extends JpaRepository<MedicalItem, Long> {
    Optional<MedicalItem> findByCode(String code);
    List<MedicalItem> findByActiveTrue();
    List<MedicalItem> findByCategoryAndActiveTrue(String category);
}
