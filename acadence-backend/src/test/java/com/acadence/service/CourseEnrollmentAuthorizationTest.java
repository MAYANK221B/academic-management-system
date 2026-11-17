package com.acadence.service;

import com.acadence.enums.CourseRole;
import com.acadence.model.CourseMembership;
import com.acadence.repository.CourseMembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentAuthorizationTest {
    @Mock CourseMembershipRepository memberships;
    @Mock PlatformAuthorizationService platformAuthorizationService;
    @InjectMocks CourseAuthorizationService service;

    @ParameterizedTest
    @CsvSource({"PROFESSOR,true,true", "TA,false,true", "STUDENT,false,false"})
    void exactOfferingRoleDeterminesPermission(CourseRole role, boolean canAddTA, boolean canAddStudent) {
        when(memberships.findByUser_UserIdAndOffering_OfferingId(7L, 101L))
                .thenReturn(Optional.of(CourseMembership.builder().role(role).build()));
        if (canAddTA) service.requireCanAddTA(7L, 101L);
        else assertDenied(() -> service.requireCanAddTA(7L, 101L));
        if (canAddStudent) service.requireCanAddStudent(7L, 101L);
        else assertDenied(() -> service.requireCanAddStudent(7L, 101L));
        verify(memberships, times(2)).findByUser_UserIdAndOffering_OfferingId(7L, 101L);
        verifyNoMoreInteractions(memberships);
    }

    @Test
    void adminCanAssignBothRolesWithoutMembership() {
        when(platformAuthorizationService.isAdmin(7L)).thenReturn(true);
        service.requireCanAddTA(7L, 101L);
        service.requireCanAddStudent(7L, 101L);
        verifyNoInteractions(memberships);
    }

    @Test
    void missingOrNullStoredRoleDeniesBothActions() {
        assertDenied(() -> service.requireCanAddTA(7L, 101L));
        assertDenied(() -> service.requireCanAddStudent(7L, 101L));
        when(memberships.findByUser_UserIdAndOffering_OfferingId(7L, 101L))
                .thenReturn(Optional.of(CourseMembership.builder().build()));
        assertDenied(() -> service.requireCanAddTA(7L, 101L));
        assertDenied(() -> service.requireCanAddStudent(7L, 101L));
    }

    @Test
    void permissionsNeverCarryAcrossOfferingsOrUsers() {
        when(memberships.findByUser_UserIdAndOffering_OfferingId(7L, 101L))
                .thenReturn(Optional.of(CourseMembership.builder().role(CourseRole.PROFESSOR).build()));
        service.requireCanAddTA(7L, 101L);
        assertDenied(() -> service.requireCanAddTA(7L, 102L));
        assertDenied(() -> service.requireCanAddStudent(7L, 102L));
        assertDenied(() -> service.requireCanAddStudent(8L, 101L));
    }

    @ParameterizedTest
    @CsvSource(value = {"null,101", "7,null", "null,null", "0,101", "-1,101", "7,0", "7,-1"}, nullValues = "null")
    void invalidIdsFailClosedBeforeAnyLookup(Long userId, Long offeringId) {
        assertDenied(() -> service.requireCanAddTA(userId, offeringId));
        assertDenied(() -> service.requireCanAddStudent(userId, offeringId));
        verifyNoInteractions(memberships, platformAuthorizationService);
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(AccessDeniedException.class);
    }
}
