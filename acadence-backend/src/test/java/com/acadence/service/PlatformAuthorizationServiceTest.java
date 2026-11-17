package com.acadence.service;

import com.acadence.enums.PlatformRole;
import com.acadence.repository.UserPlatformRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAuthorizationServiceTest {

    @Mock UserPlatformRoleRepository userPlatformRoleRepository;
    @InjectMocks PlatformAuthorizationService service;

    @Test
    void administratorIsAuthorized() {
        when(userPlatformRoleRepository.existsByUser_UserIdAndRole(42L, PlatformRole.ACADENCE_ADMIN)).thenReturn(true);
        assertThat(service.isAdmin(42L)).isTrue();
        assertThatCode(() -> service.requireAdmin(42L)).doesNotThrowAnyException();
        verify(userPlatformRoleRepository, times(2)).existsByUser_UserIdAndRole(42L, PlatformRole.ACADENCE_ADMIN);
        verifyNoMoreInteractions(userPlatformRoleRepository);
    }

    @Test
    void regularUserWithoutPlatformRoleIsDenied() {
        assertDenied(42L);
        verify(userPlatformRoleRepository, times(2)).existsByUser_UserIdAndRole(42L, PlatformRole.ACADENCE_ADMIN);
    }

    @Test
    void administratorRoleOfAnotherUserDoesNotGrantAccess() {
        when(userPlatformRoleRepository.existsByUser_UserIdAndRole(42L, PlatformRole.ACADENCE_ADMIN)).thenReturn(true);
        assertThat(service.isAdmin(42L)).isTrue();
        assertDenied(43L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void invalidIdsFailClosedWithoutDatabaseAccess(Long userId) {
        assertDenied(userId);
        verifyNoInteractions(userPlatformRoleRepository);
    }

    private void assertDenied(Long userId) {
        assertThat(service.isAdmin(userId)).isFalse();
        assertThatThrownBy(() -> service.requireAdmin(userId))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Access denied");
    }
}
