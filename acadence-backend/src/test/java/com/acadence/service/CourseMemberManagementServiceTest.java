package com.acadence.service;

import com.acadence.enums.AccountStatus;
import com.acadence.enums.CourseRole;
import com.acadence.exception.*;
import com.acadence.model.User;
import com.acadence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseMemberManagementServiceTest {
    @Mock CourseAuthorizationService authorization;
    @Mock UserRepository users;
    @Mock CourseMembershipService memberships;
    @InjectMocks CourseMemberManagementService service;

    @ParameterizedTest
    @EnumSource(value = CourseRole.class, names = {"TA", "STUDENT"})
    void authorizesBeforeLookingUpTargetAndDelegatesPersistence(CourseRole role) {
        User target = User.builder().email("target@umn.edu").accountStatus(AccountStatus.ACTIVE).build();
        ReflectionTestUtils.setField(target, "userId", 42L);
        when(users.findByEmail("target@umn.edu")).thenReturn(Optional.of(target));
        service.addMember(7L, 101L, "target@umn.edu", role);
        var order = inOrder(authorization, users, memberships);
        if (role == CourseRole.TA) order.verify(authorization).requireCanAddTA(7L, 101L);
        else order.verify(authorization).requireCanAddStudent(7L, 101L);
        order.verify(users).findByEmail("target@umn.edu");
        order.verify(memberships).addMember(42L, 101L, role);
        verifyNoMoreInteractions(authorization, users, memberships);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = CourseRole.class, names = "PROFESSOR")
    void unsupportedRoleRejectedBeforeAuthorizationAndMutation(CourseRole role) {
        assertThatThrownBy(() -> service.addMember(7L, 101L, "target@umn.edu", role))
                .isInstanceOf(UnsupportedCourseMemberRoleException.class);
        verifyNoInteractions(authorization, users, memberships);
    }

    @ParameterizedTest
    @EnumSource(value = CourseRole.class, names = {"TA", "STUDENT"})
    void unauthorizedActorCannotInspectTargetOrMutate(CourseRole role) {
        if (role == CourseRole.TA) doThrow(new AccessDeniedException("Access denied")).when(authorization).requireCanAddTA(7L, 101L);
        else doThrow(new AccessDeniedException("Access denied")).when(authorization).requireCanAddStudent(7L, 101L);
        assertThatThrownBy(() -> service.addMember(7L, 101L, "target@umn.edu", role)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, memberships);
    }

    @Test
    void missingUserIsNotCreated() {
        assertThatThrownBy(() -> service.addMember(7L, 101L, "target@umn.edu", CourseRole.STUDENT))
                .isInstanceOf(UserNotFoundException.class);
        verify(users).findByEmail("target@umn.edu");
        verifyNoMoreInteractions(users);
        verifyNoInteractions(memberships);
    }

    @Test
    void inactiveUserCannotBeEnrolled() {
        when(users.findByEmail("target@umn.edu")).thenReturn(Optional.of(User.builder().accountStatus(AccountStatus.DISABLED).build()));
        assertThatThrownBy(() -> service.addMember(7L, 101L, "target@umn.edu", CourseRole.STUDENT))
                .isInstanceOf(InactiveUserException.class);
        verifyNoInteractions(memberships);
    }
}
