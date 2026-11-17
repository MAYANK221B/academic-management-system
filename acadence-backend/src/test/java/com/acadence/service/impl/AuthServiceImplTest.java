package com.acadence.service.impl;

import com.acadence.dto.request.LoginRequest;
import com.acadence.dto.response.LoginResponse;
import com.acadence.model.User;
import com.acadence.repository.UserRepository;
import com.acadence.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @Mock UserRepository userRepository;
    @InjectMocks AuthServiceImpl authService;

    private final LoginRequest request = new LoginRequest("submitted@example.com", "test-password");

    private Authentication authenticated() {
        return UsernamePasswordAuthenticationToken.authenticated("authenticated@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void authenticatesBeforeLookupAndIssuesTokenForAuthenticatedDatabaseUser() {
        User user = mock(User.class);
        when(authenticationManager.authenticate(any())).thenReturn(authenticated());
        when(userRepository.findByEmail("authenticated@example.com")).thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn(42L);
        when(jwtService.generateAccessToken(42L)).thenReturn("signed-access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(7));

        LoginResponse response = authService.login(request);

        assertThat(response).isEqualTo(new LoginResponse(42L, "signed-access-token", "Bearer", 420L));
        var order = inOrder(authenticationManager, userRepository, jwtService);
        ArgumentCaptor<Authentication> credentials = ArgumentCaptor.forClass(Authentication.class);
        order.verify(authenticationManager).authenticate(credentials.capture());
        order.verify(userRepository).findByEmail("authenticated@example.com");
        order.verify(jwtService).generateAccessToken(42L);
        assertThat(credentials.getValue()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(credentials.getValue().getName()).isEqualTo(request.email());
        assertThat(credentials.getValue().getCredentials()).isEqualTo(request.password());
        assertThat(credentials.getValue().isAuthenticated()).isFalse();
        verify(userRepository, never()).findByEmail(request.email());
    }

    @Test
    void incorrectCredentialsPreventLookupAndTokenGeneration() {
        BadCredentialsException failure = new BadCredentialsException("Invalid credentials");
        when(authenticationManager.authenticate(any())).thenThrow(failure);

        assertThatThrownBy(() -> authService.login(request)).isSameAs(failure);

        verifyNoInteractions(userRepository, jwtService);
    }

    @Test
    void disabledAccountPreventsLookupAndTokenGeneration() {
        DisabledException failure = new DisabledException("Account disabled");
        when(authenticationManager.authenticate(any())).thenThrow(failure);

        assertThatThrownBy(() -> authService.login(request)).isSameAs(failure);

        verifyNoInteractions(userRepository, jwtService);
    }

    @Test
    void unresolvedAuthenticatedUserFailsWithoutTokenGeneration() {
        when(authenticationManager.authenticate(any())).thenReturn(authenticated());
        when(userRepository.findByEmail("authenticated@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid credentials");

        var order = inOrder(authenticationManager, userRepository);
        order.verify(authenticationManager).authenticate(any());
        order.verify(userRepository).findByEmail("authenticated@example.com");
        verifyNoInteractions(jwtService);
    }
}
