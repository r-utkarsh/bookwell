package com.bookwell.user;

import com.bookwell.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditedEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "email_verified_at")
    private java.time.Instant emailVerifiedAt;

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void markEmailVerified(java.time.Instant at) {
        this.emailVerifiedAt = at;
    }

    public static User create(String email, String passwordHash, Role role, String fullName, String phone) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = role;
        user.fullName = fullName;
        user.phone = phone;
        user.enabled = true;
        return user;
    }
}
