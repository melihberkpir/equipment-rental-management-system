package com.equipmentrental.equipment;

import com.equipmentrental.domain.Equipment;
import com.equipmentrental.domain.EquipmentStatus;
import com.equipmentrental.domain.MaintenanceStatus;
import com.equipmentrental.domain.ReservationStatus;
import com.equipmentrental.equipment.dto.AvailabilityResponse;
import com.equipmentrental.equipment.dto.AvailabilityResponse.UnavailabilityReason;
import com.equipmentrental.exception.BadRequestException;
import com.equipmentrental.exception.NotFoundException;
import com.equipmentrental.repository.EquipmentRepository;
import com.equipmentrental.repository.MaintenanceRecordRepository;
import com.equipmentrental.repository.ReservationRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    public static final Set<ReservationStatus> BLOCKING_RESERVATION_STATUSES =
            Set.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    public static final Set<MaintenanceStatus> BLOCKING_MAINTENANCE_STATUSES =
            Set.of(MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS);

    private final EquipmentRepository equipment;
    private final ReservationRepository reservations;
    private final MaintenanceRecordRepository maintenance;

    public AvailabilityService(EquipmentRepository equipment, ReservationRepository reservations,
            MaintenanceRecordRepository maintenance) {
        this.equipment = equipment;
        this.reservations = reservations;
        this.maintenance = maintenance;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse check(Long equipmentId, LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new BadRequestException("startDate must be before endDate");
        }
        Equipment item = equipment.findById(equipmentId)
                .orElseThrow(() -> new NotFoundException("Equipment not found"));

        List<UnavailabilityReason> reasons = new ArrayList<>();
        if (item.getStatus() != EquipmentStatus.ACTIVE) {
            reasons.add(UnavailabilityReason.EQUIPMENT_NOT_ACTIVE);
        }
        if (maintenance.existsOverlapping(equipmentId, startDate, endDate, BLOCKING_MAINTENANCE_STATUSES)) {
            reasons.add(UnavailabilityReason.UNDER_MAINTENANCE);
        }
        if (reservations.existsOverlapping(equipmentId, startDate, endDate, BLOCKING_RESERVATION_STATUSES)) {
            reasons.add(UnavailabilityReason.ALREADY_RESERVED);
        }
        return new AvailabilityResponse(equipmentId, startDate, endDate, reasons.isEmpty(), List.copyOf(reasons));
    }
}
