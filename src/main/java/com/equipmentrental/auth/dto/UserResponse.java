package com.equipmentrental.auth.dto;

import com.equipmentrental.domain.RoleName;

public record UserResponse(Long id, String fullName, String email, RoleName role) {
}
