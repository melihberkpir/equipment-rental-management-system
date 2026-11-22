package com.equipmentrental.reservation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReservationRequest(
        @NotNull Long equipmentId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
