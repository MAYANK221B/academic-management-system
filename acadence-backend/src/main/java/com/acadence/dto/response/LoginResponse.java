package com.acadence.dto.response;

public record LoginResponse(
        Long userId,
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
