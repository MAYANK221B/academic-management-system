package com.acadence.service;

import com.acadence.dto.response.CourseMembershipResponse;
import com.acadence.enums.AccountStatus;
import com.acadence.enums.CourseRole;
import com.acadence.exception.InactiveUserException;
import com.acadence.exception.UserNotFoundException;
import com.acadence.model.User;
import com.acadence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfessorAssignmentService {

    private final PlatformAuthorizationService platformAuthorizationService;
    private final UserRepository userRepository;
    private final CourseMembershipService courseMembershipService;

    @Transactional
    public CourseMembershipResponse assignProfessor(Long actingUserId, Long offeringId, String email) {
        platformAuthorizationService.requireAdmin(actingUserId);
        User professor = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        if (professor.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new InactiveUserException();
        }
        // Membership service checks the offering and duplicate membership before persisting.
        return courseMembershipService.addMember(professor.getUserId(), offeringId, CourseRole.PROFESSOR);
    }
}
