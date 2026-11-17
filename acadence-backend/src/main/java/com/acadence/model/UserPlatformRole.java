package com.acadence.model;

import com.acadence.enums.PlatformRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_platform_roles", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_platform_roles_user_role", columnNames = {"user_id", "role"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPlatformRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_platform_role_id")
    private Long userPlatformRoleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private PlatformRole role;

    @Builder
    public UserPlatformRole(User user, PlatformRole role) {
        this.user = user;
        this.role = role;
    }
}
