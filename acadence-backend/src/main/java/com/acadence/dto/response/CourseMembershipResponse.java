package com.acadence.dto.response;

import com.acadence.enums.CourseRole;

import java.time.Instant;

public record CourseMembershipResponse(
        Long membershipId,
        Long userId,
        Long offeringId,
        CourseRole role,
        Instant joinedAt
) {
}
