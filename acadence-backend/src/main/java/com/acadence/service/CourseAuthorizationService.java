package com.acadence.service;

import com.acadence.enums.CourseRole;
import com.acadence.model.CourseMembership;
import com.acadence.repository.CourseMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseAuthorizationService {

    private final CourseMembershipRepository courseMembershipRepository;
    private final PlatformAuthorizationService platformAuthorizationService;

    public void requireCanAddTA(Long actingUserId, Long offeringId) {
        requireCanAddMember(actingUserId, offeringId, false);
    }

    public void requireCanAddStudent(Long actingUserId, Long offeringId) {
        requireCanAddMember(actingUserId, offeringId, true);
    }

    private void requireCanAddMember(Long actingUserId, Long offeringId, boolean allowTA) {
        if (validIds(actingUserId, offeringId)) {
            if (platformAuthorizationService.isAdmin(actingUserId)) {
                return;
            }
            CourseRole role = findRole(actingUserId, offeringId);
            if (role == CourseRole.PROFESSOR || (allowTA && role == CourseRole.TA)) {
                return;
            }
        }
        throw new AccessDeniedException("Access denied");
    }

    public boolean hasRole(Long userId, Long offeringId, CourseRole role) {
        return role != null && role == findRole(userId, offeringId);
    }

    public void requireMembership(Long userId, Long offeringId) {
        if (findRole(userId, offeringId) == null) {
            throw new AccessDeniedException("Access denied");
        }
    }

    public void requireTeachingStaff(Long userId, Long offeringId) {
        CourseRole role = findRole(userId, offeringId);
        if (role != CourseRole.TA && role != CourseRole.PROFESSOR) {
            throw new AccessDeniedException("Access denied");
        }
    }

    public void requireProfessor(Long userId, Long offeringId) {
        if (!hasRole(userId, offeringId, CourseRole.PROFESSOR)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private CourseRole findRole(Long userId, Long offeringId) {
        if (!validIds(userId, offeringId)) {
            return null;
        }
        return courseMembershipRepository.findByUser_UserIdAndOffering_OfferingId(userId, offeringId)
                .map(CourseMembership::getRole).orElse(null);
    }

    private boolean validIds(Long userId, Long offeringId) {
        return userId != null && offeringId != null && userId > 0 && offeringId > 0;
    }
}
