package com.equipmentrental.maintenance.dto;

import com.equipmentrental.domain.MaintenanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MaintenanceRequest(
        @NotBlank @Size(max = 5000) String description,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        MaintenanceStatus status) {
}
