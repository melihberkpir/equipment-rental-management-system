package com.equipmentrental.equipment;

import com.equipmentrental.common.PageResponse;
import com.equipmentrental.domain.EquipmentStatus;
import com.equipmentrental.equipment.dto.AvailabilityResponse;
import com.equipmentrental.equipment.dto.EquipmentFilter;
import com.equipmentrental.equipment.dto.EquipmentRequest;
import com.equipmentrental.equipment.dto.EquipmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipment")
@Tag(name = "Equipment")
public class EquipmentController {

    private final EquipmentService service;
    private final AvailabilityService availabilityService;

    public EquipmentController(EquipmentService service, AvailabilityService availabilityService) {
        this.service = service;
        this.availabilityService = availabilityService;
    }

    @GetMapping
    @Operation(summary = "Browse and filter equipment",
            description = "Sort format: field,asc or field,desc. Sortable fields: id, name, dailyRate, status.")
    public PageResponse<EquipmentResponse> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minRate,
            @RequestParam(required = false) BigDecimal maxRate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort) {
        return service.list(new EquipmentFilter(categoryId, status, q, minRate, maxRate), page, limit, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get equipment details")
    public EquipmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Check availability for a date range",
            description = "The end date is exclusive, matching the rental day calculation.")
    public AvailabilityResponse availability(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return availabilityService.check(id, startDate, endDate);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create equipment (ADMIN)")
    public EquipmentResponse create(@Valid @RequestBody EquipmentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update equipment (ADMIN)")
    public EquipmentResponse update(@PathVariable Long id, @Valid @RequestBody EquipmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete equipment without history (ADMIN)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
