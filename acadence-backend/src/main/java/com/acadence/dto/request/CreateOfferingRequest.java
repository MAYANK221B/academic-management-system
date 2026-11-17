package com.acadence.dto.request;

import com.acadence.enums.Semester;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOfferingRequest(
        @NotNull Semester semester,
        @NotNull @Positive Integer year,
        @NotBlank @Size(max = 20) String section
) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected request field");
    }
}
