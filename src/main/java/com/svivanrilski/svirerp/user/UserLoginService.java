package com.svivanrilski.svirerp.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLoginService {

    private final AppUserRepository userRepository;
    private final UserLoginHistoryRepository historyRepository;

    public List<AppUser> listUsers() {
        return userRepository.findAllByOrderByLastLoginAtDesc();
    }

    public List<UserLoginHistory> listRecentLogins() {
        return historyRepository.findTop50ByOrderByLoginAtDesc();
    }

    @Transactional
    public AppUser recordLogin(String rawEmail, String name, String provider) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return null;
        }
        String email = rawEmail.trim().toLowerCase();
        OffsetDateTime now = OffsetDateTime.now();

        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    existing.setLastLoginAt(now);
                    existing.setLoginCount((existing.getLoginCount() != null ? existing.getLoginCount() : 0) + 1);
                    if (name != null && !name.isBlank()) {
                        existing.setName(name);
                    }
                    if (provider != null && !provider.isBlank()) {
                        existing.setProvider(provider);
                    }
                    return existing;
                })
                .orElseGet(() -> AppUser.builder()
                        .email(email)
                        .name(name != null && !name.isBlank() ? name : email)
                        .provider(provider != null && !provider.isBlank() ? provider : "unknown")
                        .lastLoginAt(now)
                        .loginCount(1)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());

        AppUser savedUser = userRepository.save(user);

        UserLoginHistory history = UserLoginHistory.builder()
                .appUser(savedUser)
                .email(email)
                .loginAt(now)
                .provider(provider != null && !provider.isBlank() ? provider : "unknown")
                .build();
        historyRepository.save(history);

        log.info("Recorded login for user={} provider={}", email, provider);
        return savedUser;
    }

    @Transactional
    public void recordInitialIfAbsent(String rawEmail, String name, String provider) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return;
        }
        String email = rawEmail.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            recordLogin(email, name, provider);
        }
    }
}
