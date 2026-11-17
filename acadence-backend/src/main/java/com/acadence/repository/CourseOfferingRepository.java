package com.acadence.repository;

import com.acadence.enums.Semester;
import com.acadence.model.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    boolean existsByCourse_CourseIdAndSemesterAndYearAndSection(
            Long courseId, Semester semester, Integer year, String section);

    List<CourseOffering> findByCourse_CourseId(Long courseId);
}
