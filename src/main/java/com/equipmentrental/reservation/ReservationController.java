package com.equipmentrental.reservation;

import com.equipmentrental.common.PageResponse;
import com.equipmentrental.domain.ReservationStatus;
import com.equipmentrental.reservation.dto.ReservationRequest;
import com.equipmentrental.reservation.dto.ReservationResponse;
import com.equipmentrental.reservation.dto.ReservationStatusRequest;
import com.equipmentrental.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a reservation",
            description = "The end date is exclusive. The total is daily rate multiplied by rental days.")
    public ReservationResponse create(@Valid @RequestBody ReservationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(request, user);
    }

    @GetMapping
    @Operation(summary = "List reservations", description = "Customers see their own records; admins see all.")
    public PageResponse<ReservationResponse> list(@RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(status, page, limit, sort, user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation (owner or ADMIN)")
    public ReservationResponse get(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(id, user);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update reservation status",
            description = "Customers can cancel their own reservations before the start date. "
                    + "Admins can confirm, complete, or cancel.")
    public ReservationResponse updateStatus(@PathVariable Long id, @Valid @RequestBody ReservationStatusRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return service.updateStatus(id, request.status(), user);
    }
}
