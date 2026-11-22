package com.equipmentrental.reservation;

import com.equipmentrental.common.PageRequests;
import com.equipmentrental.common.PageResponse;
import com.equipmentrental.domain.Equipment;
import com.equipmentrental.domain.EquipmentStatus;
import com.equipmentrental.domain.Payment;
import com.equipmentrental.domain.PaymentStatus;
import com.equipmentrental.domain.Reservation;
import com.equipmentrental.domain.ReservationStatus;
import com.equipmentrental.domain.RoleName;
import com.equipmentrental.equipment.AvailabilityService;
import com.equipmentrental.equipment.dto.AvailabilityResponse;
import com.equipmentrental.exception.BadRequestException;
import com.equipmentrental.exception.ConflictException;
import com.equipmentrental.exception.NotFoundException;
import com.equipmentrental.repository.EquipmentRepository;
import com.equipmentrental.repository.PaymentRepository;
import com.equipmentrental.repository.ReservationRepository;
import com.equipmentrental.repository.UserRepository;
import com.equipmentrental.reservation.dto.ReservationRequest;
import com.equipmentrental.reservation.dto.ReservationResponse;
import com.equipmentrental.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "startDate", "endDate", "status", "totalCost");
    private static final BigDecimal MAX_TOTAL_COST = new BigDecimal("9999999999.99");

    private final ReservationRepository reservations;
    private final EquipmentRepository equipment;
    private final UserRepository users;
    private final PaymentRepository payments;
    private final AvailabilityService availability;

    public ReservationService(ReservationRepository reservations, EquipmentRepository equipment,
            UserRepository users, PaymentRepository payments, AvailabilityService availability) {
        this.reservations = reservations;
        this.equipment = equipment;
        this.users = users;
        this.payments = payments;
        this.availability = availability;
    }

    @Transactional
    public ReservationResponse create(ReservationRequest request, AuthenticatedUser currentUser) {
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (!start.isBefore(end)) {
            throw new BadRequestException("startDate must be before endDate");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new BadRequestException("startDate must not be in the past");
        }
        Equipment item = equipment.findByIdForUpdate(request.equipmentId())
                .orElseThrow(() -> new NotFoundException("Equipment not found"));
        AvailabilityResponse result = availability.check(item.getId(), start, end);
        if (!result.available()) {
            throw new ConflictException("Equipment is not available for the selected dates: " + result.reasons());
        }
        Reservation reservation = new Reservation(users.getReferenceById(currentUser.getId()), item, start, end,
                calculateTotal(item.getDailyRate(), start, end));
        return ReservationResponse.from(reservations.saveAndFlush(reservation));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> list(ReservationStatus status, int page, int limit, String sort,
            AuthenticatedUser currentUser) {
        Long ownerId = isAdmin(currentUser) ? null : currentUser.getId();
        return PageResponse.from(reservations.findAll(matching(ownerId, status),
                PageRequests.of(page, limit, sort, SORTABLE_FIELDS, "startDate")), ReservationResponse::from);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long id, AuthenticatedUser currentUser) {
        Reservation reservation = reservations.findWithEquipmentById(id)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        requireOwnerOrAdmin(reservation, currentUser);
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse updateStatus(Long id, ReservationStatus target, AuthenticatedUser currentUser) {
        Reservation reservation = reservations.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        requireOwnerOrAdmin(reservation, currentUser);
        LocalDate today = LocalDate.now();

        if (!isAdmin(currentUser)) {
            if (target != ReservationStatus.CANCELLED) {
                throw new AccessDeniedException("Customers can only cancel reservations");
            }
            if (!today.isBefore(reservation.getStartDate())) {
                throw new ConflictException("Reservations can only be cancelled before the start date");
            }
        }
        ReservationStatus current = reservation.getStatus();
        if (!isAllowedTransition(current, target)) {
            throw new ConflictException("Reservation cannot change from " + current + " to " + target);
        }
        if (target == ReservationStatus.CONFIRMED
                && reservation.getEquipment().getStatus() != EquipmentStatus.ACTIVE) {
            throw new ConflictException("Reservation cannot be confirmed while equipment is not active");
        }
        if (target == ReservationStatus.COMPLETED && today.isBefore(reservation.getStartDate())) {
            throw new ConflictException("Reservation cannot be completed before the start date");
        }

        reservation.setStatus(target);
        recordPaymentChange(reservation, current, target);
        return ReservationResponse.from(reservations.saveAndFlush(reservation));
    }

    /** Confirmation records the payment; cancelling a confirmed reservation records its refund. */
    private void recordPaymentChange(Reservation reservation, ReservationStatus previous, ReservationStatus target) {
        if (target == ReservationStatus.CONFIRMED) {
            Payment payment = new Payment(reservation, reservation.getTotalCost());
            payment.setStatus(PaymentStatus.PAID);
            payments.save(payment);
        } else if (target == ReservationStatus.CANCELLED && previous == ReservationStatus.CONFIRMED) {
            payments.findByReservationIdAndStatus(reservation.getId(), PaymentStatus.PAID)
                    .forEach(payment -> payment.setStatus(PaymentStatus.REFUNDED));
        }
    }

    private static boolean isAllowedTransition(ReservationStatus current, ReservationStatus target) {
        return switch (current) {
            case PENDING -> target == ReservationStatus.CONFIRMED || target == ReservationStatus.CANCELLED;
            case CONFIRMED -> target == ReservationStatus.COMPLETED || target == ReservationStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };
    }

    private static BigDecimal calculateTotal(BigDecimal dailyRate, LocalDate start, LocalDate end) {
        BigDecimal total = dailyRate.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end)))
                .setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(MAX_TOTAL_COST) > 0) {
            throw new BadRequestException("Reservation total is too large");
        }
        return total;
    }

    private static Specification<Reservation> matching(Long ownerId, ReservationStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ownerId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), ownerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void requireOwnerOrAdmin(Reservation reservation, AuthenticatedUser currentUser) {
        if (!isAdmin(currentUser) && !reservation.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this reservation");
        }
    }

    private static boolean isAdmin(AuthenticatedUser user) {
        return user.getRole() == RoleName.ADMIN;
    }
}
