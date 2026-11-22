package com.equipmentrental.repository;

import com.equipmentrental.domain.Role;
import com.equipmentrental.domain.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
