package com.acadence.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves the internal ID from a JWT already verified by Spring Security. */
public final class JwtUserIdResolver {

    private JwtUserIdResolver() {
    }

    public static Long resolve(Jwt jwt) {
        try {
            Long userId = jwt == null ? null : Long.valueOf(jwt.getSubject());
            if (userId == null || userId <= 0) {
                throw new NumberFormatException();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
        }
    }
}
