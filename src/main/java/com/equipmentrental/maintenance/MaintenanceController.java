package com.equipmentrental.maintenance;

import com.equipmentrental.common.PageResponse;
import com.equipmentrental.maintenance.dto.MaintenanceRequest;
import com.equipmentrental.maintenance.dto.MaintenanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/equipment/{equipmentId}/maintenance")
@Tag(name = "Maintenance")
public class MaintenanceController {

    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List maintenance records for equipment")
    public PageResponse<MaintenanceResponse> list(@PathVariable Long equipmentId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort) {
        return service.list(equipmentId, page, limit, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a maintenance record (ADMIN)")
    public MaintenanceResponse create(@PathVariable Long equipmentId,
            @Valid @RequestBody MaintenanceRequest request) {
        return service.create(equipmentId, request);
    }

    @PutMapping("/{maintenanceId}")
    @Operation(summary = "Update a maintenance record (ADMIN)")
    public MaintenanceResponse update(@PathVariable Long equipmentId, @PathVariable Long maintenanceId,
            @Valid @RequestBody MaintenanceRequest request) {
        return service.update(equipmentId, maintenanceId, request);
    }
}
