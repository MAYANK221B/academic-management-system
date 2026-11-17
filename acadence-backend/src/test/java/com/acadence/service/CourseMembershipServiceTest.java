package com.acadence.service;

import com.acadence.dto.response.CourseMembershipResponse;
import com.acadence.enums.CourseRole;
import com.acadence.exception.CourseOfferingNotFoundException;
import com.acadence.exception.DuplicateCourseMembershipException;
import com.acadence.exception.UserNotFoundException;
import com.acadence.mapper.CourseMembershipMapper;
import com.acadence.model.CourseMembership;
import com.acadence.model.CourseOffering;
import com.acadence.model.User;
import com.acadence.repository.CourseMembershipRepository;
import com.acadence.repository.CourseOfferingRepository;
import com.acadence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseMembershipServiceTest {
    @Mock UserRepository userRepository;
    @Mock CourseOfferingRepository courseOfferingRepository;
    @Mock CourseMembershipRepository courseMembershipRepository;
    @Spy CourseMembershipMapper courseMembershipMapper = Mappers.getMapper(CourseMembershipMapper.class);
    @InjectMocks CourseMembershipService service;

    private User user() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 42L);
        return user;
    }

    private CourseOffering offering() {
        CourseOffering offering = CourseOffering.builder().build();
        ReflectionTestUtils.setField(offering, "offeringId", 101L);
        return offering;
    }

    private CourseMembership savedMembership(CourseRole role) {
        CourseMembership membership = CourseMembership.builder().user(user()).offering(offering()).role(role).build();
        ReflectionTestUtils.setField(membership, "membershipId", 7L);
        ReflectionTestUtils.setField(membership, "joinedAt", Instant.parse("2026-01-01T00:00:00Z"));
        return membership;
    }

    private void existingParents() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user()));
        when(courseOfferingRepository.findById(101L)).thenReturn(Optional.of(offering()));
    }

    @ParameterizedTest
    @EnumSource(CourseRole.class)
    void createsMembershipAndMapsSavedValues(CourseRole role) {
        existingParents();
        when(courseMembershipRepository.save(any())).thenAnswer(invocation -> {
            CourseMembership membership = invocation.getArgument(0);
            assertThat(membership.getUser().getUserId()).isEqualTo(42L);
            assertThat(membership.getOffering().getOfferingId()).isEqualTo(101L);
            assertThat(membership.getRole()).isEqualTo(role);
            assertThat(membership.getMembershipId()).isNull();
            return savedMembership(role);
        });
        assertThat(service.addMember(42L, 101L, role)).isEqualTo(new CourseMembershipResponse(
                7L, 42L, 101L, role, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void missingUserPreventsCreation() {
        assertThatThrownBy(() -> service.addMember(42L, 101L, CourseRole.STUDENT))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(courseOfferingRepository, courseMembershipRepository, courseMembershipMapper);
    }

    @Test
    void missingOfferingPreventsCreation() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user()));
        assertThatThrownBy(() -> service.addMember(42L, 101L, CourseRole.STUDENT))
                .isInstanceOf(CourseOfferingNotFoundException.class);
        verifyNoInteractions(courseMembershipRepository, courseMembershipMapper);
    }

    @Test
    void duplicateMembershipPreventsSavingEvenForDifferentRole() {
        existingParents();
        when(courseMembershipRepository.existsByUser_UserIdAndOffering_OfferingId(42L, 101L)).thenReturn(true);
        assertThatThrownBy(() -> service.addMember(42L, 101L, CourseRole.TA))
                .isInstanceOf(DuplicateCourseMembershipException.class);
        verify(courseMembershipRepository, never()).save(any());
        verifyNoInteractions(courseMembershipMapper);
    }

    @Test
    void nullRoleIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.addMember(42L, 101L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Course membership role is required");
        verifyNoInteractions(userRepository, courseOfferingRepository, courseMembershipRepository, courseMembershipMapper);
    }

    @Test
    void concurrentDatabaseDuplicateIsNotSwallowed() {
        existingParents();
        var failure = new DataIntegrityViolationException("Concurrent duplicate");
        when(courseMembershipRepository.save(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.addMember(42L, 101L, CourseRole.STUDENT)).isSameAs(failure);
        verifyNoInteractions(courseMembershipMapper);
    }

    @Test
    void listsOfferingMembersAndHandlesEmptyOffering() {
        when(courseOfferingRepository.existsById(101L)).thenReturn(true);
        when(courseMembershipRepository.findByOffering_OfferingId(101L))
                .thenReturn(List.of(savedMembership(CourseRole.PROFESSOR))).thenReturn(List.of());
        assertThat(service.getMembersByOffering(101L)).extracting(CourseMembershipResponse::userId).containsExactly(42L);
        assertThat(service.getMembersByOffering(101L)).isEmpty();
    }

    @Test
    void rejectsListingMissingOffering() {
        assertThatThrownBy(() -> service.getMembersByOffering(101L)).isInstanceOf(CourseOfferingNotFoundException.class);
        verifyNoInteractions(courseMembershipRepository, courseMembershipMapper);
    }

    @Test
    void listsUserMembershipsAndHandlesUserWithNoMemberships() {
        when(userRepository.existsById(42L)).thenReturn(true);
        when(courseMembershipRepository.findByUser_UserId(42L))
                .thenReturn(List.of(savedMembership(CourseRole.STUDENT))).thenReturn(List.of());
        assertThat(service.getMembershipsByUser(42L)).extracting(CourseMembershipResponse::offeringId).containsExactly(101L);
        assertThat(service.getMembershipsByUser(42L)).isEmpty();
    }

    @Test
    void rejectsListingMissingUser() {
        assertThatThrownBy(() -> service.getMembershipsByUser(42L)).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(courseMembershipRepository, courseMembershipMapper);
    }
}
