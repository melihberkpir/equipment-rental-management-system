package com.equipmentrental.repository;

import com.equipmentrental.domain.Payment;
import com.equipmentrental.domain.PaymentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByReservationIdAndStatus(Long reservationId, PaymentStatus status);
}
