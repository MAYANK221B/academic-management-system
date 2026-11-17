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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseMembershipService {

    private final UserRepository userRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseMembershipRepository courseMembershipRepository;
    private final CourseMembershipMapper courseMembershipMapper;

    @Transactional
    public CourseMembershipResponse addMember(Long userId, Long offeringId, CourseRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Course membership role is required");
        }
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        CourseOffering offering = courseOfferingRepository.findById(offeringId)
                .orElseThrow(CourseOfferingNotFoundException::new);
        if (courseMembershipRepository.existsByUser_UserIdAndOffering_OfferingId(userId, offeringId)) {
            throw new DuplicateCourseMembershipException();
        }
        CourseMembership membership = CourseMembership.builder().user(user).offering(offering).role(role).build();
        return courseMembershipMapper.toResponse(courseMembershipRepository.save(membership));
    }

    @Transactional(readOnly = true)
    public List<CourseMembershipResponse> getMembersByOffering(Long offeringId) {
        if (!courseOfferingRepository.existsById(offeringId)) {
            throw new CourseOfferingNotFoundException();
        }
        return courseMembershipRepository.findByOffering_OfferingId(offeringId).stream()
                .map(courseMembershipMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseMembershipResponse> getMembershipsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }
        return courseMembershipRepository.findByUser_UserId(userId).stream()
                .map(courseMembershipMapper::toResponse).toList();
    }
}
