package com.equipmentrental.payment;

import com.equipmentrental.common.PageResponse;
import com.equipmentrental.domain.PaymentStatus;
import com.equipmentrental.payment.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List recorded payments (ADMIN)")
    public PageResponse<PaymentResponse> list(@RequestParam(required = false) Long reservationId,
            @RequestParam(required = false) PaymentStatus status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String sort) {
        return service.list(reservationId, status, page, limit, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a recorded payment (ADMIN)")
    public PaymentResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
