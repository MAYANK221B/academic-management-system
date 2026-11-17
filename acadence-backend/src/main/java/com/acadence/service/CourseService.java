package com.acadence.service;

import com.acadence.dto.request.CreateCourseRequest;
import com.acadence.dto.response.CourseResponse;
import com.acadence.exception.CourseNotFoundException;
import com.acadence.exception.DuplicateCourseCodeException;
import com.acadence.mapper.CourseMapper;
import com.acadence.model.Course;
import com.acadence.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;
    private final PlatformAuthorizationService platformAuthorizationService;

    @Transactional
    public CourseResponse createCourse(Long actingUserId, CreateCourseRequest request) {
        platformAuthorizationService.requireAdmin(actingUserId);
        if (courseRepository.existsByCourseCode(request.courseCode())) {
            throw new DuplicateCourseCodeException();
        }
        Course course = Course.builder().courseCode(request.courseCode()).courseName(request.courseName())
                .description(request.description()).build();
        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourseById(Long courseId) {
        return courseMapper.toResponse(courseRepository.findById(courseId)
                .orElseThrow(CourseNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream().map(courseMapper::toResponse).toList();
    }
}
