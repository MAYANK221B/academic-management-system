package com.acadence.dto.response;

import java.time.Instant;

public record CourseResponse(
        Long courseId,
        String courseCode,
        String courseName,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
