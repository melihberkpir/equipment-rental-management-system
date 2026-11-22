package com.equipmentrental.equipment;

import com.equipmentrental.common.PageRequests;
import com.equipmentrental.common.PageResponse;
import com.equipmentrental.domain.Category;
import com.equipmentrental.domain.Equipment;
import com.equipmentrental.equipment.dto.EquipmentFilter;
import com.equipmentrental.equipment.dto.EquipmentRequest;
import com.equipmentrental.equipment.dto.EquipmentResponse;
import com.equipmentrental.exception.BadRequestException;
import com.equipmentrental.exception.ConflictException;
import com.equipmentrental.exception.NotFoundException;
import com.equipmentrental.repository.CategoryRepository;
import com.equipmentrental.repository.EquipmentRepository;
import com.equipmentrental.repository.MaintenanceRecordRepository;
import com.equipmentrental.repository.ReservationRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "dailyRate", "status");

    private final EquipmentRepository equipment;
    private final CategoryRepository categories;
    private final ReservationRepository reservations;
    private final MaintenanceRecordRepository maintenance;

    public EquipmentService(EquipmentRepository equipment, CategoryRepository categories,
            ReservationRepository reservations, MaintenanceRecordRepository maintenance) {
        this.equipment = equipment;
        this.categories = categories;
        this.reservations = reservations;
        this.maintenance = maintenance;
    }

    @Transactional(readOnly = true)
    public PageResponse<EquipmentResponse> list(EquipmentFilter filter, int page, int limit, String sort) {
        if (filter.minRate() != null && filter.maxRate() != null
                && filter.minRate().compareTo(filter.maxRate()) > 0) {
            throw new BadRequestException("minRate must not be greater than maxRate");
        }
        return PageResponse.from(equipment.findAll(EquipmentSpecifications.matching(filter),
                PageRequests.of(page, limit, sort, SORTABLE_FIELDS, "name")), EquipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public EquipmentResponse get(Long id) {
        return EquipmentResponse.from(findWithCategory(id));
    }

    @Transactional
    public EquipmentResponse create(EquipmentRequest request) {
        Equipment item = new Equipment(findCategory(request.categoryId()), request.name().strip(),
                request.description(), request.dailyRate());
        if (request.status() != null) {
            item.setStatus(request.status());
        }
        return EquipmentResponse.from(equipment.saveAndFlush(item));
    }

    @Transactional
    public EquipmentResponse update(Long id, EquipmentRequest request) {
        Equipment item = findWithCategory(id);
        item.setCategory(findCategory(request.categoryId()));
        item.setName(request.name().strip());
        item.setDescription(request.description());
        item.setDailyRate(request.dailyRate());
        if (request.status() != null) {
            item.setStatus(request.status());
        }
        return EquipmentResponse.from(equipment.saveAndFlush(item));
    }

    @Transactional
    public void delete(Long id) {
        Equipment item = equipment.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Equipment not found"));
        if (reservations.existsByEquipmentId(id) || maintenance.existsByEquipmentId(id)) {
            throw new ConflictException("Equipment with reservation or maintenance history cannot be deleted");
        }
        equipment.delete(item);
        equipment.flush();
    }

    private Equipment findWithCategory(Long id) {
        return equipment.findWithCategoryById(id).orElseThrow(() -> new NotFoundException("Equipment not found"));
    }

    private Category findCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found"));
    }
}
