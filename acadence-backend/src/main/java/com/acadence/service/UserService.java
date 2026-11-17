package com.acadence.service;

import com.acadence.dto.request.RegisterUserRequest;
import com.acadence.dto.response.UserResponse;

public interface UserService {

    UserResponse registerStudent(RegisterUserRequest request);

    UserResponse getCurrentUser(Long userId);
}
