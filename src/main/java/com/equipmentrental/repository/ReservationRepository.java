package com.equipmentrental.repository;

import com.equipmentrental.domain.Reservation;
import com.equipmentrental.domain.ReservationStatus;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByEquipmentId(Long equipmentId);

    /** Checks the half-open date range [start, end). */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.equipment.id = :equipmentId
              and r.status in :statuses
              and r.startDate < :end
              and r.endDate > :start
            """)
    boolean existsOverlapping(@Param("equipmentId") Long equipmentId, @Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("statuses") Collection<ReservationStatus> statuses);
}
