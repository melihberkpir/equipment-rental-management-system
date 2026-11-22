package com.equipmentrental.agreement;

import com.equipmentrental.domain.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

record AgreementData(Long reservationId, LocalDate issueDate, String customerName, String customerEmail,
        String equipmentName, String categoryName, BigDecimal dailyRate, LocalDate startDate, LocalDate endDate,
        long rentalDays, BigDecimal totalCost, ReservationStatus status) {
}
