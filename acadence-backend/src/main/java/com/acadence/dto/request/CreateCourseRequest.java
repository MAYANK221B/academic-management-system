package com.acadence.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank @Size(max = 30) String courseCode,
        @NotBlank @Size(max = 255) String courseName,
        String description
) {
}
