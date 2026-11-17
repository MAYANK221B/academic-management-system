package com.acadence.mapper;

import com.acadence.dto.response.CourseMembershipResponse;
import com.acadence.model.CourseMembership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CourseMembershipMapper {

    @Mapping(target = "userId", source = "user.userId")
    @Mapping(target = "offeringId", source = "offering.offeringId")
    CourseMembershipResponse toResponse(CourseMembership membership);
}
