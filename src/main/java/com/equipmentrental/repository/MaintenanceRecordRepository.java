package com.equipmentrental.repository;

import com.equipmentrental.domain.MaintenanceRecord;
import com.equipmentrental.domain.MaintenanceStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    Page<MaintenanceRecord> findByEquipmentId(Long equipmentId, Pageable pageable);

    Optional<MaintenanceRecord> findByIdAndEquipmentId(Long id, Long equipmentId);

    boolean existsByEquipmentId(Long equipmentId);

    /** Checks a rental range [start, end) against inclusive maintenance dates; a null end is open-ended. */
    @Query("""
            select count(m) > 0 from MaintenanceRecord m
            where m.equipment.id = :equipmentId
              and m.status in :statuses
              and m.startDate < :end
              and (m.endDate is null or m.endDate >= :start)
            """)
    boolean existsOverlapping(@Param("equipmentId") Long equipmentId, @Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("statuses") Collection<MaintenanceStatus> statuses);
}
