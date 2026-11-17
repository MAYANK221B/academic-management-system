package com.acadence.service.impl;

import com.acadence.dto.request.LoginRequest;
import com.acadence.dto.response.LoginResponse;
import com.acadence.model.User;
import com.acadence.repository.UserRepository;
import com.acadence.security.JwtService;
import com.acadence.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        String token = jwtService.generateAccessToken(user.getUserId());
        return new LoginResponse(user.getUserId(), token, "Bearer",
                jwtService.getAccessTokenExpiration().toSeconds());
    }
}
