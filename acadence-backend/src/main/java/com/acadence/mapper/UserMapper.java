package com.acadence.mapper;

import com.acadence.dto.response.UserResponse;
import com.acadence.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
