package com.acadence.service.impl;

import com.acadence.dto.response.UserResponse;
import com.acadence.enums.AccountStatus;
import com.acadence.exception.UserNotFoundException;
import com.acadence.mapper.UserMapper;
import com.acadence.model.User;
import com.acadence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserServiceImpl service;

    @Test
    void currentUserLoadsAndMapsActiveUser() {
        User user = mock(User.class);
        UserResponse response = new UserResponse(101L, "Alex", "alex@example.com", AccountStatus.ACTIVE, Instant.now());
        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(userMapper.toResponse(user)).thenReturn(response);

        assertThat(service.getCurrentUser(101L)).isSameAs(response);
        verify(userMapper).toResponse(user);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void deletedUserFailsWithoutMapping() {
        when(userRepository.findById(101L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCurrentUser(101L)).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(userMapper);
    }

    @Test
    void disabledUserFailsWithoutMapping() {
        User user = mock(User.class);
        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(user.getAccountStatus()).thenReturn(AccountStatus.DISABLED);
        assertThatThrownBy(() -> service.getCurrentUser(101L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userMapper);
    }
}
