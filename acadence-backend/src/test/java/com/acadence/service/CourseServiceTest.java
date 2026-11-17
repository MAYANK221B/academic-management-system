package com.acadence.service;

import com.acadence.dto.request.CreateCourseRequest;
import com.acadence.exception.CourseNotFoundException;
import com.acadence.exception.DuplicateCourseCodeException;
import com.acadence.mapper.CourseMapper;
import com.acadence.model.Course;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock CourseRepository courseRepository;
    @Mock PlatformAuthorizationService platformAuthorizationService;
    @Spy CourseMapper courseMapper = Mappers.getMapper(CourseMapper.class);
    @InjectMocks CourseService service;

    private Course savedCourse() {
        Course course = Course.builder().courseCode("CS101").courseName("Computing").description("Introduction").build();
        ReflectionTestUtils.setField(course, "courseId", 42L);
        ReflectionTestUtils.setField(course, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(course, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));
        return course;
    }

    @Test
    void createsCourseAndMapsSavedValues() {
        when(courseRepository.save(any())).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            assertThat(course.getCourseCode()).isEqualTo("CS101");
            assertThat(course.getCourseName()).isEqualTo("Computing");
            assertThat(course.getDescription()).isEqualTo("Introduction");
            assertThat(course.getCourseId()).isNull();
            return savedCourse();
        });
        var response = service.createCourse(7L, new CreateCourseRequest("CS101", "Computing", "Introduction"));
        assertThat(response.courseId()).isEqualTo(42L);
        assertThat(response.courseCode()).isEqualTo("CS101");
        assertThat(response.courseName()).isEqualTo("Computing");
        assertThat(response.description()).isEqualTo("Introduction");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
    }

    @Test
    void rejectsDuplicateCodeBeforeSaving() {
        when(courseRepository.existsByCourseCode("CS101")).thenReturn(true);
        assertThatThrownBy(() -> service.createCourse(7L, new CreateCourseRequest("CS101", "Computing", null)))
                .isInstanceOf(DuplicateCourseCodeException.class);
        verify(courseRepository, never()).save(any());
        verifyNoInteractions(courseMapper);
    }

    @Test
    void databaseConstraintFailuresAreNotSwallowed() {
        var failure = new DataIntegrityViolationException("Concurrent duplicate");
        when(courseRepository.save(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.createCourse(7L, new CreateCourseRequest("CS101", "Computing", null)))
                .isSameAs(failure);
        verifyNoInteractions(courseMapper);
    }

    @Test
    void creationChecksAuthorizationBeforePersistence() {
        service.createCourse(7L, new CreateCourseRequest("CS101", "Computing", null));
        var order = inOrder(platformAuthorizationService, courseRepository);
        order.verify(platformAuthorizationService).requireAdmin(7L);
        order.verify(courseRepository).existsByCourseCode("CS101");
        order.verify(courseRepository).save(any());
    }

    @Test
    void unauthorizedServiceCallerCannotCreateCourse() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Access denied"))
                .when(platformAuthorizationService).requireAdmin(8L);
        assertThatThrownBy(() -> service.createCourse(8L, new CreateCourseRequest("CS101", "Computing", null)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(courseRepository, courseMapper);
    }

    @Test
    void retrievesCourseById() {
        when(courseRepository.findById(42L)).thenReturn(Optional.of(savedCourse()));
        assertThat(service.getCourseById(42L).courseCode()).isEqualTo("CS101");
    }

    @Test
    void rejectsMissingCourse() {
        when(courseRepository.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCourseById(42L)).isInstanceOf(CourseNotFoundException.class);
        verifyNoInteractions(courseMapper);
    }

    @Test
    void listsCoursesAndHandlesEmptyCatalog() {
        when(courseRepository.findAll()).thenReturn(List.of(savedCourse())).thenReturn(List.of());
        assertThat(service.getAllCourses()).extracting(response -> response.courseId()).containsExactly(42L);
        assertThat(service.getAllCourses()).isEmpty();
    }
}
