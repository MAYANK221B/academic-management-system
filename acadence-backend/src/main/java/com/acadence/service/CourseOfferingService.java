package com.acadence.service;

import com.acadence.dto.request.CreateCourseOfferingRequest;
import com.acadence.dto.response.CourseOfferingResponse;
import com.acadence.exception.CourseNotFoundException;
import com.acadence.exception.CourseOfferingNotFoundException;
import com.acadence.exception.DuplicateCourseOfferingException;
import com.acadence.mapper.CourseOfferingMapper;
import com.acadence.model.Course;
import com.acadence.model.CourseOffering;
import com.acadence.repository.CourseOfferingRepository;
import com.acadence.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseOfferingService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingMapper courseOfferingMapper;
    private final PlatformAuthorizationService platformAuthorizationService;

    @Transactional
    public CourseOfferingResponse createOffering(Long actingUserId, CreateCourseOfferingRequest request) {
        platformAuthorizationService.requireAdmin(actingUserId);
        Course course = courseRepository.findById(request.courseId()).orElseThrow(CourseNotFoundException::new);
        if (courseOfferingRepository.existsByCourse_CourseIdAndSemesterAndYearAndSection(
                request.courseId(), request.semester(), request.year(), request.section())) {
            throw new DuplicateCourseOfferingException();
        }
        CourseOffering offering = CourseOffering.builder().course(course).semester(request.semester())
                .year(request.year()).section(request.section()).build();
        return courseOfferingMapper.toResponse(courseOfferingRepository.save(offering));
    }

    @Transactional(readOnly = true)
    public CourseOfferingResponse getOfferingById(Long offeringId) {
        return courseOfferingMapper.toResponse(courseOfferingRepository.findById(offeringId)
                .orElseThrow(CourseOfferingNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingResponse> getOfferingsByCourseId(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new CourseNotFoundException();
        }
        return courseOfferingRepository.findByCourse_CourseId(courseId).stream()
                .map(courseOfferingMapper::toResponse).toList();
    }
}
