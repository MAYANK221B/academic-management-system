package com.acadence.service;

import com.acadence.dto.request.LoginRequest;
import com.acadence.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
