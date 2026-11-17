package com.acadence.dto.request;

import com.acadence.enums.Semester;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCourseOfferingRequest(
        @NotNull @Positive Long courseId,
        @NotNull Semester semester,
        @NotNull @Positive Integer year,
        @NotBlank @Size(max = 20) String section
) {
}
