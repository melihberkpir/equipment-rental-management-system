package com.equipmentrental.maintenance;

import com.equipmentrental.common.PageRequests;
import com.equipmentrental.common.PageResponse;
import com.equipmentrental.domain.Equipment;
import com.equipmentrental.domain.MaintenanceRecord;
import com.equipmentrental.domain.MaintenanceStatus;
import com.equipmentrental.equipment.AvailabilityService;
import com.equipmentrental.exception.BadRequestException;
import com.equipmentrental.exception.ConflictException;
import com.equipmentrental.exception.NotFoundException;
import com.equipmentrental.maintenance.dto.MaintenanceRequest;
import com.equipmentrental.maintenance.dto.MaintenanceResponse;
import com.equipmentrental.repository.EquipmentRepository;
import com.equipmentrental.repository.MaintenanceRecordRepository;
import com.equipmentrental.repository.ReservationRepository;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "startDate", "endDate", "status");
    private static final LocalDate OPEN_ENDED_LIMIT = LocalDate.of(9999, 12, 31);

    private final MaintenanceRecordRepository records;
    private final EquipmentRepository equipment;
    private final ReservationRepository reservations;

    public MaintenanceService(MaintenanceRecordRepository records, EquipmentRepository equipment,
            ReservationRepository reservations) {
        this.records = records;
        this.equipment = equipment;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceResponse> list(Long equipmentId, int page, int limit, String sort) {
        if (!equipment.existsById(equipmentId)) {
            throw new NotFoundException("Equipment not found");
        }
        return PageResponse.from(records.findByEquipmentId(equipmentId,
                PageRequests.of(page, limit, sort, SORTABLE_FIELDS, "startDate")), MaintenanceResponse::from);
    }

    @Transactional
    public MaintenanceResponse create(Long equipmentId, MaintenanceRequest request) {
        Equipment item = lockEquipment(equipmentId);
        MaintenanceStatus status = request.status() != null ? request.status() : MaintenanceStatus.SCHEDULED;
        validateSchedule(equipmentId, request, status);

        MaintenanceRecord record = new MaintenanceRecord(item, request.description().strip(), request.startDate(),
                request.endDate());
        record.setStatus(status);
        return MaintenanceResponse.from(records.saveAndFlush(record));
    }

    @Transactional
    public MaintenanceResponse update(Long equipmentId, Long maintenanceId, MaintenanceRequest request) {
        lockEquipment(equipmentId);
        MaintenanceRecord record = records.findByIdAndEquipmentId(maintenanceId, equipmentId)
                .orElseThrow(() -> new NotFoundException("Maintenance record not found"));
        MaintenanceStatus status = request.status() != null ? request.status() : record.getStatus();
        validateSchedule(equipmentId, request, status);

        record.setDescription(request.description().strip());
        record.setStartDate(request.startDate());
        record.setEndDate(request.endDate());
        record.setStatus(status);
        return MaintenanceResponse.from(records.saveAndFlush(record));
    }

    private Equipment lockEquipment(Long equipmentId) {
        return equipment.findByIdForUpdate(equipmentId)
                .orElseThrow(() -> new NotFoundException("Equipment not found"));
    }

    private void validateSchedule(Long equipmentId, MaintenanceRequest request, MaintenanceStatus status) {
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (end != null && end.isBefore(start)) {
            throw new BadRequestException("endDate must not be before startDate");
        }
        if (end != null && !end.isBefore(OPEN_ENDED_LIMIT)) {
            throw new BadRequestException("endDate is too far in the future");
        }
        if (!AvailabilityService.BLOCKING_MAINTENANCE_STATUSES.contains(status)) {
            return;
        }
        // Maintenance end dates are inclusive, while the reservation query uses an exclusive end.
        LocalDate exclusiveEnd = end != null ? end.plusDays(1) : OPEN_ENDED_LIMIT;
        if (reservations.existsOverlapping(equipmentId, start, exclusiveEnd,
                AvailabilityService.BLOCKING_RESERVATION_STATUSES)) {
            throw new ConflictException("Maintenance period overlaps an active reservation");
        }
    }
}
