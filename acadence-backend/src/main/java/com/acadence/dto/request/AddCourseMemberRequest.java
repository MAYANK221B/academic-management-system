package com.acadence.dto.request;

import com.acadence.enums.CourseRole;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddCourseMemberRequest(@NotBlank @Email String email, @NotNull CourseRole role) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected request field");
    }
}
