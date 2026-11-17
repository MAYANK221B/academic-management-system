package com.acadence.repository;

import com.acadence.enums.PlatformRole;
import com.acadence.model.UserPlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPlatformRoleRepository extends JpaRepository<UserPlatformRole, Long> {

    boolean existsByUser_UserIdAndRole(Long userId, PlatformRole role);
}
