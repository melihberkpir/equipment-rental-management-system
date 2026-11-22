package com.equipmentrental.reservation.dto;

import com.equipmentrental.domain.Reservation;
import com.equipmentrental.domain.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ReservationResponse(Long id, Long userId, Long equipmentId, String equipmentName, LocalDate startDate,
        LocalDate endDate, long rentalDays, ReservationStatus status, BigDecimal totalCost) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getUser().getId(),
                reservation.getEquipment().getId(), reservation.getEquipment().getName(),
                reservation.getStartDate(), reservation.getEndDate(),
                ChronoUnit.DAYS.between(reservation.getStartDate(), reservation.getEndDate()),
                reservation.getStatus(), reservation.getTotalCost());
    }
}
