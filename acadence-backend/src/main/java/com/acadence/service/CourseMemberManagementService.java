package com.acadence.service;

import com.acadence.dto.response.CourseMembershipResponse;
import com.acadence.enums.AccountStatus;
import com.acadence.enums.CourseRole;
import com.acadence.exception.InactiveUserException;
import com.acadence.exception.UnsupportedCourseMemberRoleException;
import com.acadence.exception.UserNotFoundException;
import com.acadence.model.User;
import com.acadence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseMemberManagementService {

    private final CourseAuthorizationService courseAuthorizationService;
    private final UserRepository userRepository;
    private final CourseMembershipService courseMembershipService;

    @Transactional
    public CourseMembershipResponse addMember(Long actingUserId, Long offeringId, String email, CourseRole role) {
        if (role == CourseRole.TA) {
            courseAuthorizationService.requireCanAddTA(actingUserId, offeringId);
        } else if (role == CourseRole.STUDENT) {
            courseAuthorizationService.requireCanAddStudent(actingUserId, offeringId);
        } else {
            throw new UnsupportedCourseMemberRoleException();
        }
        User target = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        if (target.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new InactiveUserException();
        }
        return courseMembershipService.addMember(target.getUserId(), offeringId, role);
    }
}
