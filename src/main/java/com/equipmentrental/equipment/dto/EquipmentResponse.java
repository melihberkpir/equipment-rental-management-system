package com.equipmentrental.equipment.dto;

import com.equipmentrental.category.dto.CategoryResponse;
import com.equipmentrental.domain.Equipment;
import com.equipmentrental.domain.EquipmentStatus;
import java.math.BigDecimal;

public record EquipmentResponse(Long id, CategoryResponse category, String name, String description,
        BigDecimal dailyRate, EquipmentStatus status) {

    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(equipment.getId(), CategoryResponse.from(equipment.getCategory()),
                equipment.getName(), equipment.getDescription(), equipment.getDailyRate(), equipment.getStatus());
    }
}
