package com.acadence.service.impl;

import com.acadence.dto.request.RegisterUserRequest;
import com.acadence.dto.response.UserResponse;
import com.acadence.enums.AccountStatus;
import com.acadence.exception.UserNotFoundException;
import com.acadence.mapper.UserMapper;
import com.acadence.model.User;
import com.acadence.repository.UserRepository;
import com.acadence.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new AccessDeniedException("Access denied");
        }
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse registerStudent(RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            // Temporary exception until an application-specific duplicate-email exception exists.
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }
}
