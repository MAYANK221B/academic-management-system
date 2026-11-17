package com.acadence.service;

import com.acadence.enums.PlatformRole;
import com.acadence.repository.UserPlatformRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAuthorizationService {

    private final UserPlatformRoleRepository userPlatformRoleRepository;

    public boolean isAdmin(Long userId) {
        return userId != null && userId > 0
                && userPlatformRoleRepository.existsByUser_UserIdAndRole(userId, PlatformRole.ACADENCE_ADMIN);
    }

    public void requireAdmin(Long userId) {
        if (!isAdmin(userId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
