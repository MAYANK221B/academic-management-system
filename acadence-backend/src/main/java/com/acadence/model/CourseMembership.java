package com.acadence.model;

import com.acadence.enums.CourseRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "course_memberships", uniqueConstraints = @UniqueConstraint(
        name = "uk_course_memberships_user_offering", columnNames = {"user_id", "offering_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "membership_id")
    private Long membershipId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private CourseRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Builder
    public CourseMembership(User user, CourseOffering offering, CourseRole role) {
        this.user = user;
        this.offering = offering;
        this.role = role;
    }
}
