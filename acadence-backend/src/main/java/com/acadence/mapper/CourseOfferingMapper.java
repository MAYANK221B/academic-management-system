package com.acadence.mapper;

import com.acadence.dto.response.CourseOfferingResponse;
import com.acadence.model.CourseOffering;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CourseOfferingMapper {

    @Mapping(target = "courseId", source = "course.courseId")
    @Mapping(target = "courseCode", source = "course.courseCode")
    @Mapping(target = "courseName", source = "course.courseName")
    CourseOfferingResponse toResponse(CourseOffering offering);
}
