package com.medicaldrone.repository;

import com.medicaldrone.model.Mission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mission m where m.id = :id")
    Optional<Mission> findByIdForUpdate(@Param("id") Long id);
    List<Mission> findByStatusIn(List<String> statuses);
    Optional<Mission> findByMissionCode(String missionCode);
    Optional<Mission> findByRequestId(Long requestId);
    List<Mission> findByStatus(String status);
    List<Mission> findAllByOrderByCreatedAtDesc();
}
