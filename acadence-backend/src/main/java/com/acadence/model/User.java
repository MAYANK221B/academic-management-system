    package com.acadence.model;

    import com.acadence.enums.AccountStatus;
    import jakarta.persistence.*;
    import lombok.*;
    import org.hibernate.annotations.CreationTimestamp;
    import org.hibernate.annotations.UpdateTimestamp;

    import java.time.Instant;

    @Entity
    @Table(name = "users")
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public class User {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long userId;
        @Column(name = "full_name", nullable = false, length = 100)
        private String fullName;
        @Column(name = "email", nullable = false, unique = true, length = 255)
        private String email;
        @Column(name = "password_hash", nullable = false, length = 255)
        private String passwordHash;
        @Enumerated(EnumType.STRING)
        @Column(name = "account_status", nullable = false, length = 20)
        private AccountStatus accountStatus ;
        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @UpdateTimestamp
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        @Builder
        public User(String fullName,
                    String email,
                    String passwordHash,
                    AccountStatus accountStatus) {

            this.fullName = fullName;
            this.email = email;
            this.passwordHash = passwordHash;
            this.accountStatus = accountStatus;
        }

        public void disable() {
            this.accountStatus = AccountStatus.DISABLED;
        }
    }
