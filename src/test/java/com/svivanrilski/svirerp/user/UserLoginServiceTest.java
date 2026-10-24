package com.svivanrilski.svirerp.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private UserLoginHistoryRepository historyRepository;

    @InjectMocks
    private UserLoginService userLoginService;

    @Test
    void recordLogin_newGoogleUser_createsAppUserAndHistory() {
        String email = "john.doe@svivanrilski.com";
        String name = "John Doe";
        String provider = "google";

        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(UserLoginHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser user = userLoginService.recordLogin(email, name, provider);

        assertThat(user).isNotNull();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getProvider()).isEqualTo("google");
        assertThat(user.getLoginCount()).isEqualTo(1);
        assertThat(user.getLastLoginAt()).isNotNull();

        verify(userRepository).save(any(AppUser.class));
        verify(historyRepository).save(any(UserLoginHistory.class));
    }

    @Test
    void recordLogin_existingUser_incrementsCountAndUpdatesTime() {
        String email = "john.doe@svivanrilski.com";
        OffsetDateTime pastTime = OffsetDateTime.now().minusDays(2);
        AppUser existing = AppUser.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("John")
                .provider("google")
                .loginCount(3)
                .lastLoginAt(pastTime)
                .build();

        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(UserLoginHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser updated = userLoginService.recordLogin(email, "John Doe", "google");

        assertThat(updated.getLoginCount()).isEqualTo(4);
        assertThat(updated.getName()).isEqualTo("John Doe");
        assertThat(updated.getLastLoginAt()).isAfter(pastTime);

        verify(userRepository).save(existing);
        verify(historyRepository).save(any(UserLoginHistory.class));
    }

    @Test
    void recordInitialIfAbsent_whenAbsent_recordsLogin() {
        String email = "admin";
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        userLoginService.recordInitialIfAbsent(email, "Local Admin", "local-admin");

        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void recordInitialIfAbsent_whenPresent_doesNotCallSave() {
        String email = "admin";
        AppUser existing = AppUser.builder().email(email).build();
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));

        userLoginService.recordInitialIfAbsent(email, "Local Admin", "local-admin");

        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void listUsers_delegatesToRepository() {
        AppUser u = AppUser.builder().email("test@example.com").build();
        when(userRepository.findAllByOrderByLastLoginAtDesc()).thenReturn(List.of(u));

        List<AppUser> result = userLoginService.listUsers();

        assertThat(result).containsExactly(u);
    }
}
