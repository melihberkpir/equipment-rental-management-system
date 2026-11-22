package com.equipmentrental.payment;

import com.equipmentrental.common.PageRequests;
import com.equipmentrental.common.PageResponse;
import com.equipmentrental.domain.Payment;
import com.equipmentrental.domain.PaymentStatus;
import com.equipmentrental.exception.NotFoundException;
import com.equipmentrental.payment.dto.PaymentResponse;
import com.equipmentrental.repository.PaymentRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "amount", "paymentDate", "status");

    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(Long reservationId, PaymentStatus status, int page, int limit,
            String sort) {
        return PageResponse.from(payments.findAll(matching(reservationId, status),
                PageRequests.of(page, limit, sort, SORTABLE_FIELDS, "paymentDate")), PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return payments.findById(id).map(PaymentResponse::from)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
    }

    private static Specification<Payment> matching(Long reservationId, PaymentStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (reservationId != null) {
                predicates.add(cb.equal(root.get("reservation").get("id"), reservationId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
