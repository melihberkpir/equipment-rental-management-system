package com.equipmentrental.equipment.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(Long equipmentId, LocalDate startDate, LocalDate endDate, boolean available,
        List<UnavailabilityReason> reasons) {

    public enum UnavailabilityReason {
        EQUIPMENT_NOT_ACTIVE, UNDER_MAINTENANCE, ALREADY_RESERVED
    }
}
