package com.equipmentrental.payment.dto;

import com.equipmentrental.domain.Payment;
import com.equipmentrental.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long reservationId, BigDecimal amount, Instant paymentDate,
        PaymentStatus status) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getReservation().getId(), payment.getAmount(),
                payment.getPaymentDate(), payment.getStatus());
    }
}
