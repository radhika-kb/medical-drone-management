package com.medicaldrone.repository;

import com.medicaldrone.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentReference = :reference")
    Optional<Payment> findByPaymentReferenceForUpdate(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.gatewayOrderId = :orderId")
    Optional<Payment> findByGatewayOrderIdForUpdate(@Param("orderId") String gatewayOrderId);
    List<Payment> findByRequestId(Long requestId);
    List<Payment> findByPayerHospitalId(Long payerHospitalId);
    Optional<Payment> findByPaymentReference(String paymentReference);
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
    Optional<Payment> findFirstByRequestIdAndPaymentStatusOrderByCreatedAtDesc(Long requestId, String paymentStatus);
}
