package com.acadence.mapper;

import com.acadence.dto.response.CourseResponse;
import com.acadence.model.Course;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CourseMapper {

    CourseResponse toResponse(Course course);
}
