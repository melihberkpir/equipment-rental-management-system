package com.equipmentrental.repository;

import com.equipmentrental.domain.Equipment;
import jakarta.persistence.LockModeType;
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

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Equipment> findAll(Specification<Equipment> specification, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Equipment> findWithCategoryById(Long id);

    boolean existsByCategoryId(Long categoryId);

    /** Serializes schedule changes for one equipment item. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);
}
