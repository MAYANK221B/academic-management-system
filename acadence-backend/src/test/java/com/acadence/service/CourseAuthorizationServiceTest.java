package com.acadence.service;

import com.acadence.enums.CourseRole;
import com.acadence.model.CourseMembership;
import com.acadence.repository.CourseMembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseAuthorizationServiceTest {

    @Mock CourseMembershipRepository courseMembershipRepository;
    @Mock PlatformAuthorizationService platformAuthorizationService;
    @InjectMocks CourseAuthorizationService service;

    private void membership(Long userId, Long offeringId, CourseRole role) {
        when(courseMembershipRepository.findByUser_UserIdAndOffering_OfferingId(userId, offeringId))
                .thenReturn(Optional.of(CourseMembership.builder().role(role).build()));
    }

    @ParameterizedTest
    @EnumSource(CourseRole.class)
    void eachCourseRoleGrantsMembershipAndOnlyItsExactRole(CourseRole role) {
        membership(42L, 101L, role);
        assertThatCode(() -> service.requireMembership(42L, 101L)).doesNotThrowAnyException();
        for (CourseRole requested : CourseRole.values()) {
            assertThat(service.hasRole(42L, 101L, requested)).isEqualTo(requested == role);
        }
    }

    @ParameterizedTest
    @EnumSource(value = CourseRole.class, names = {"TA", "PROFESSOR"})
    void teachingStaffCanAccessStaffFunctions(CourseRole role) {
        membership(42L, 101L, role);
        assertThatCode(() -> service.requireTeachingStaff(42L, 101L)).doesNotThrowAnyException();
    }

    @Test
    void professorCanAccessProfessorFunctions() {
        membership(42L, 101L, CourseRole.PROFESSOR);
        assertThatCode(() -> service.requireProfessor(42L, 101L)).doesNotThrowAnyException();
    }

    @Test
    void studentCannotAccessTeachingStaffFunctions() {
        membership(42L, 101L, CourseRole.STUDENT);
        assertDenied(() -> service.requireTeachingStaff(42L, 101L));
        assertDenied(() -> service.requireProfessor(42L, 101L));
    }

    @Test
    void taCannotAccessProfessorFunctions() {
        membership(42L, 101L, CourseRole.TA);
        assertDenied(() -> service.requireProfessor(42L, 101L));
    }

    @Test
    void missingMembershipDeniesEveryPermission() {
        assertAllDenied(42L, 101L);
        verify(courseMembershipRepository, times(4))
                .findByUser_UserIdAndOffering_OfferingId(42L, 101L);
        verifyNoMoreInteractions(courseMembershipRepository);
    }

    @Test
    void rolesAreScopedToTheExactUserAndOffering() {
        membership(42L, 101L, CourseRole.STUDENT);
        membership(42L, 102L, CourseRole.TA);
        service.requireMembership(42L, 101L);
        assertDenied(() -> service.requireTeachingStaff(42L, 101L));
        service.requireTeachingStaff(42L, 102L);
        assertDenied(() -> service.requireProfessor(42L, 102L));
        assertAllDenied(42L, 103L);
        assertAllDenied(43L, 102L);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,101", "42,null", "null,null", "0,101", "42,-1"}, nullValues = "null")
    void invalidIdsFailClosedWithoutDatabaseAccess(Long userId, Long offeringId) {
        assertAllDenied(userId, offeringId);
        verifyNoInteractions(courseMembershipRepository);
    }

    @Test
    void nullRequestedRoleNeverGrantsAccess() {
        assertThat(service.hasRole(42L, 101L, null)).isFalse();
        verifyNoInteractions(courseMembershipRepository);
    }

    @Test
    void nullStoredRoleNeverGrantsAccess() {
        membership(42L, 101L, null);
        assertAllDenied(42L, 101L);
    }

    private void assertAllDenied(Long userId, Long offeringId) {
        assertThat(service.hasRole(userId, offeringId, CourseRole.STUDENT)).isFalse();
        assertDenied(() -> service.requireMembership(userId, offeringId));
        assertDenied(() -> service.requireTeachingStaff(userId, offeringId));
        assertDenied(() -> service.requireProfessor(userId, offeringId));
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(AccessDeniedException.class).hasMessage("Access denied");
    }
}
