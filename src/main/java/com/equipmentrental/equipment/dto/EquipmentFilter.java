package com.equipmentrental.equipment.dto;

import com.equipmentrental.domain.EquipmentStatus;
import java.math.BigDecimal;

public record EquipmentFilter(Long categoryId, EquipmentStatus status, String q, BigDecimal minRate,
        BigDecimal maxRate) {
}
