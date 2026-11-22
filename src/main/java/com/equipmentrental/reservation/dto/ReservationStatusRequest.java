package com.equipmentrental.reservation.dto;

import com.equipmentrental.domain.ReservationStatus;
import jakarta.validation.constraints.NotNull;

public record ReservationStatusRequest(@NotNull ReservationStatus status) {
}
