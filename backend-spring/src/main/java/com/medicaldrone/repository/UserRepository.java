package com.medicaldrone.repository;

import com.medicaldrone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);
    List<User> findByHospitalId(Long hospitalId);
    List<User> findByRoleNameAndStatus(String roleName, String status);
}
