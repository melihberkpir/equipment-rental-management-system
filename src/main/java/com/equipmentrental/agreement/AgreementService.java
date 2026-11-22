package com.equipmentrental.agreement;

import com.equipmentrental.domain.Reservation;
import com.equipmentrental.domain.ReservationStatus;
import com.equipmentrental.domain.RoleName;
import com.equipmentrental.exception.ConflictException;
import com.equipmentrental.exception.NotFoundException;
import com.equipmentrental.repository.ReservationRepository;
import com.equipmentrental.security.AuthenticatedUser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AgreementService {

    private static final Set<ReservationStatus> AGREEMENT_STATUSES =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED);

    private final ReservationRepository reservations;
    private final AgreementPdfGenerator generator;
    private final Clock clock;

    public AgreementService(ReservationRepository reservations, AgreementPdfGenerator generator, Clock clock) {
        this.reservations = reservations;
        this.generator = generator;
        this.clock = clock;
    }

    /** The query fetches every association, so PDF rendering does not hold a database transaction. */
    public AgreementFile generate(Long reservationId, AuthenticatedUser currentUser) {
        Reservation reservation = reservations.findForAgreement(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        if (currentUser.getRole() != RoleName.ADMIN
                && !reservation.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this reservation");
        }
        if (!AGREEMENT_STATUSES.contains(reservation.getStatus())) {
            throw new ConflictException("An agreement is available only for confirmed or completed reservations");
        }
        byte[] content = generator.generate(new AgreementData(reservation.getId(), LocalDate.now(clock),
                reservation.getUser().getFullName(), reservation.getUser().getEmail(),
                reservation.getEquipment().getName(), reservation.getEquipment().getCategory().getName(),
                reservation.getEquipment().getDailyRate(), reservation.getStartDate(), reservation.getEndDate(),
                ChronoUnit.DAYS.between(reservation.getStartDate(), reservation.getEndDate()),
                reservation.getTotalCost(), reservation.getStatus()));
        return new AgreementFile("rental-agreement-" + reservation.getId() + ".pdf", content);
    }
}
