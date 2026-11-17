package com.acadence.dto.response;

import com.acadence.enums.AccountStatus;

import java.time.Instant;

public record UserResponse(
        Long userId,
        String fullName,
        String email,
        AccountStatus accountStatus,
        Instant createdAt
) {
}
