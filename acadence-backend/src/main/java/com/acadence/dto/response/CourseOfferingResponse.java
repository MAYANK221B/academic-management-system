package com.acadence.dto.response;

import com.acadence.enums.Semester;

public record CourseOfferingResponse(
        Long offeringId,
        Long courseId,
        String courseCode,
        String courseName,
        Semester semester,
        Integer year,
        String section
) {
}
