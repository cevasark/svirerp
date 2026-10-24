package com.svivanrilski.svirerp.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_login_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id")
    private AppUser appUser;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "login_at", nullable = false)
    private OffsetDateTime loginAt;

    @Column(length = 50)
    private String provider;

    @PrePersist
    private void prePersist() {
        if (loginAt == null) {
            loginAt = OffsetDateTime.now();
        }
    }
}
