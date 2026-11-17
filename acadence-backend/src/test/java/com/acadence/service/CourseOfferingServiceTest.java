package com.acadence.service;

import com.acadence.dto.request.CreateCourseOfferingRequest;
import com.acadence.enums.Semester;
import com.acadence.exception.CourseNotFoundException;
import com.acadence.exception.CourseOfferingNotFoundException;
import com.acadence.exception.DuplicateCourseOfferingException;
import com.acadence.mapper.CourseOfferingMapper;
import com.acadence.model.Course;
import com.acadence.model.CourseOffering;
import com.acadence.repository.CourseOfferingRepository;
import com.acadence.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseOfferingServiceTest {
    @Mock CourseOfferingRepository courseOfferingRepository;
    @Mock CourseRepository courseRepository;
    @Mock PlatformAuthorizationService platformAuthorizationService;
    @Spy CourseOfferingMapper courseOfferingMapper = Mappers.getMapper(CourseOfferingMapper.class);
    @InjectMocks CourseOfferingService service;

    private final CreateCourseOfferingRequest request = new CreateCourseOfferingRequest(42L, Semester.FALL, 2026, "A");

    private Course course() {
        Course course = Course.builder().courseCode("CS101").courseName("Computing").build();
        ReflectionTestUtils.setField(course, "courseId", 42L);
        return course;
    }

    private CourseOffering offering() {
        CourseOffering offering = CourseOffering.builder().course(course()).semester(Semester.FALL)
                .year(2026).section("A").build();
        ReflectionTestUtils.setField(offering, "offeringId", 101L);
        return offering;
    }

    @Test
    void createsOfferingAndMapsCourseFields() {
        Course course = course();
        when(courseRepository.findById(42L)).thenReturn(Optional.of(course));
        when(courseOfferingRepository.save(any())).thenAnswer(invocation -> {
            CourseOffering offering = invocation.getArgument(0);
            assertThat(offering.getCourse()).isSameAs(course);
            assertThat(offering.getSemester()).isEqualTo(Semester.FALL);
            assertThat(offering.getYear()).isEqualTo(2026);
            assertThat(offering.getSection()).isEqualTo("A");
            return offering();
        });
        var response = service.createOffering(7L, request);
        var order = inOrder(platformAuthorizationService, courseRepository, courseOfferingRepository);
        order.verify(platformAuthorizationService).requireAdmin(7L);
        order.verify(courseRepository).findById(42L);
        order.verify(courseOfferingRepository).existsByCourse_CourseIdAndSemesterAndYearAndSection(
                42L, Semester.FALL, 2026, "A");
        order.verify(courseOfferingRepository).save(any());
        assertThat(response.offeringId()).isEqualTo(101L);
        assertThat(response.courseId()).isEqualTo(42L);
        assertThat(response.courseCode()).isEqualTo("CS101");
        assertThat(response.courseName()).isEqualTo("Computing");
        assertThat(response.semester()).isEqualTo(Semester.FALL);
        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.section()).isEqualTo("A");
    }

    @Test
    void unauthorizedCallerCannotReachCourseLookupOrPersistence() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Access denied"))
                .when(platformAuthorizationService).requireAdmin(8L);
        assertThatThrownBy(() -> service.createOffering(8L, request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(courseRepository, courseOfferingRepository, courseOfferingMapper);
    }

    @Test
    void missingCoursePreventsOfferingCreation() {
        when(courseRepository.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createOffering(7L, request)).isInstanceOf(CourseNotFoundException.class);
        verifyNoInteractions(courseOfferingRepository, courseOfferingMapper);
    }

    @Test
    void rejectsExactDuplicateOfferingBeforeSaving() {
        when(courseRepository.findById(42L)).thenReturn(Optional.of(course()));
        when(courseOfferingRepository.existsByCourse_CourseIdAndSemesterAndYearAndSection(42L, Semester.FALL, 2026, "A"))
                .thenReturn(true);
        assertThatThrownBy(() -> service.createOffering(7L, request)).isInstanceOf(DuplicateCourseOfferingException.class);
        verify(courseOfferingRepository, never()).save(any());
        verifyNoInteractions(courseOfferingMapper);
    }

    @Test
    void databaseConstraintFailuresAreNotSwallowed() {
        when(courseRepository.findById(42L)).thenReturn(Optional.of(course()));
        var failure = new DataIntegrityViolationException("Concurrent duplicate");
        when(courseOfferingRepository.save(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.createOffering(7L, request)).isSameAs(failure);
        verifyNoInteractions(courseOfferingMapper);
    }

    @Test
    void retrievesOfferingById() {
        when(courseOfferingRepository.findById(101L)).thenReturn(Optional.of(offering()));
        assertThat(service.getOfferingById(101L).courseId()).isEqualTo(42L);
    }

    @Test
    void rejectsMissingOffering() {
        when(courseOfferingRepository.findById(101L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOfferingById(101L)).isInstanceOf(CourseOfferingNotFoundException.class);
        verifyNoInteractions(courseOfferingMapper);
    }

    @Test
    void listsOfferingsForExistingCourseAndHandlesEmptyList() {
        when(courseRepository.existsById(42L)).thenReturn(true);
        when(courseOfferingRepository.findByCourse_CourseId(42L)).thenReturn(List.of(offering())).thenReturn(List.of());
        assertThat(service.getOfferingsByCourseId(42L)).extracting(response -> response.offeringId()).containsExactly(101L);
        assertThat(service.getOfferingsByCourseId(42L)).isEmpty();
    }

    @Test
    void listingOfferingsForMissingCourseFails() {
        assertThatThrownBy(() -> service.getOfferingsByCourseId(42L)).isInstanceOf(CourseNotFoundException.class);
        verifyNoInteractions(courseOfferingRepository, courseOfferingMapper);
    }
}
