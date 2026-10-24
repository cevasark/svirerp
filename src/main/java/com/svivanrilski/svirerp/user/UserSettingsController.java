package com.svivanrilski.svirerp.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/settings/users")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserLoginService userLoginService;

    @GetMapping
    public List<UserSummaryResponse> listUsers() {
        return userLoginService.listUsers().stream()
                .map(u -> new UserSummaryResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getName(),
                        u.getProvider(),
                        u.getLastLoginAt(),
                        u.getLoginCount(),
                        u.getCreatedAt()
                ))
                .toList();
    }

    public record UserSummaryResponse(
            UUID id,
            String email,
            String name,
            String provider,
            OffsetDateTime lastLoginAt,
            Integer loginCount,
            OffsetDateTime createdAt
    ) {}
}
