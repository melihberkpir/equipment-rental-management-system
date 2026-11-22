package com.equipmentrental.repository;

import com.equipmentrental.domain.Reservation;
import com.equipmentrental.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long>,
        JpaSpecificationExecutor<Reservation> {

    @Override
    @EntityGraph(attributePaths = "equipment")
    Page<Reservation> findAll(Specification<Reservation> specification, Pageable pageable);

    @EntityGraph(attributePaths = "equipment")
    Optional<Reservation> findWithEquipmentById(Long id);

    /** Serializes status changes so a reservation cannot be confirmed and cancelled concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select r from Reservation r
            join fetch r.user
            join fetch r.equipment e
            join fetch e.category
            where r.id = :id
            """)
    Optional<Reservation> findForAgreement(@Param("id") Long id);

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
