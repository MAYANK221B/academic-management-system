package com.acadence.repository;

import com.acadence.model.CourseMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseMembershipRepository extends JpaRepository<CourseMembership, Long> {

    boolean existsByUser_UserIdAndOffering_OfferingId(Long userId, Long offeringId);

    Optional<CourseMembership> findByUser_UserIdAndOffering_OfferingId(Long userId, Long offeringId);

    List<CourseMembership> findByOffering_OfferingId(Long offeringId);

    // Each membership exposes its associated offering and offering-specific role.
    List<CourseMembership> findByUser_UserId(Long userId);
}
