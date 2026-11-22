package com.equipmentrental.maintenance.dto;

import com.equipmentrental.domain.MaintenanceRecord;
import com.equipmentrental.domain.MaintenanceStatus;
import java.time.LocalDate;

public record MaintenanceResponse(Long id, Long equipmentId, String description, LocalDate startDate,
        LocalDate endDate, MaintenanceStatus status) {

    public static MaintenanceResponse from(MaintenanceRecord record) {
        return new MaintenanceResponse(record.getId(), record.getEquipment().getId(), record.getDescription(),
                record.getStartDate(), record.getEndDate(), record.getStatus());
    }
}
