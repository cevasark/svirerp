package com.svivanrilski.svirerp.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/auth/me doubles as both "who am I" and "am I logged in" for the SPA:
 * since /api/** already requires authentication, an unauthenticated call
 * never reaches this controller — it gets JsonAuthenticationEntryPoint's
 * 401 instead, which Angular treats as the "not logged in" signal.
 */
@RestController
@RequestMapping("/api/auth")
@lombok.RequiredArgsConstructor
public class AuthController {

    private final com.svivanrilski.svirerp.user.UserLoginService userLoginService;

    @GetMapping("/me")
    public AuthenticatedUserResponse me(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            userLoginService.recordInitialIfAbsent(oidcUser.getEmail(), oidcUser.getFullName(), "google");
            return new AuthenticatedUserResponse(oidcUser.getEmail(), oidcUser.getFullName(), "google");
        }
        userLoginService.recordInitialIfAbsent(authentication.getName(), "Local Admin", "local-admin");
        return new AuthenticatedUserResponse(null, authentication.getName(), "local-admin");
    }

    public record AuthenticatedUserResponse(String email, String name, String provider) {
    }
}
