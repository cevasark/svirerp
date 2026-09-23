package com.svivanrilski.svirerp.auth;

import com.svivanrilski.svirerp.settings.AppSettingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Login is a gate, not a permission system: everything except /api/** is
 * public (the Angular shell and static assets must load before the SPA can
 * even show a login prompt), and once authenticated — via Google (in the
 * allowed hosted domain) or the local admin fallback — every user gets the
 * same full API access.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GoogleHostedDomainOidcUserService googleHostedDomainOidcUserService;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final LocalAdminSecurityConfig localAdminSecurityConfig;
    private final AppSettingService settingService;

    /**
     * Resolved fresh on every OAuth2 login attempt rather than built once at
     * startup — Google's client-id/secret are configured at runtime via the
     * admin-only Settings page (app_setting table), not application.properties,
     * so a rotated secret takes effect immediately with no restart. Providing
     * this bean explicitly means Spring Boot's own property-based OAuth2 client
     * auto-configuration backs off (@ConditionalOnMissingBean) — there must be
     * zero spring.security.oauth2.client.registration.* properties left for
     * that to work cleanly (see application.properties).
     */
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return registrationId -> {
            if (!"google".equals(registrationId)) {
                return null;
            }
            String clientId = settingService.getDecryptedValue("google.oauth.client-id").orElse(null);
            String clientSecret = settingService.getDecryptedValue("google.oauth.client-secret").orElse(null);
            if (clientId == null || clientSecret == null) {
                return null;
            }
            // CommonOAuth2Provider.GOOGLE (the usual shortcut for these well-known
            // endpoints) was removed from this Spring Security version's public
            // API, so the same stable, publicly-documented Google OIDC endpoints
            // are declared explicitly here instead.
            return ClientRegistration.withRegistrationId("google")
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "email", "profile")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .userNameAttributeName("sub")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .issuerUri("https://accounts.google.com")
                    .clientName("Google")
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/settings/**", "/api/organization", "/api/organization/**").hasRole("ADMIN")
                        // Stripe calls this server-to-server with no session — authenticated
                        // instead by the payload signature (see StripeWebhookController). Must be
                        // matched before the blanket /api/** authenticated() rule below.
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleHostedDomainOidcUserService)))
                // Spring's default formLogin success/failure handlers issue redirects, which
                // XHR follows transparently and reports back as a confusing 200-with-HTML-body
                // rather than a clean success/failure signal — override both so the Angular
                // client gets a plain status code instead.
                .formLogin(form -> form
                        .loginProcessingUrl("/login/local")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) -> response.sendError(
                                exception instanceof LockedException ? 429 : 401, exception.getMessage()))
                        .permitAll())
                // Spring Security's CsrfConfigurer#spa() convenience method isn't available in
                // the 6.5.x line bundled with this Spring Boot version, so this wires the same
                // effect by hand: CookieCsrfTokenRepository puts the token in a JS-readable
                // XSRF-TOKEN cookie. CsrfTokenRequestAttributeHandler alone is NOT enough —
                // it only exposes the token as a deferred request attribute; the cookie is
                // still never actually written until something forces resolution. Plain GETs
                // never force it (CsrfFilter only resolves the token to validate unsafe
                // methods), so without CsrfCookieFilter below, a cold browser's very first
                // POST/PUT/DELETE always 403s because no cookie has ever been issued.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Stripe's webhook POST carries no XSRF-TOKEN cookie (it's not a browser
                        // request) — verified instead by the Stripe-Signature header.
                        .ignoringRequestMatchers("/api/webhooks/**"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(jsonAuthenticationEntryPoint, new AntPathRequestMatcher("/api/**")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));

        localAdminSecurityConfig.buildProvider().ifPresent(http::authenticationProvider);

        return http.build();
    }

    /** Forces the deferred CSRF token to resolve on every request, so the XSRF-TOKEN
     * cookie is always present by the time the client needs to send it back — see the
     * comment on the .csrf(...) call above for why this is necessary. */
    private static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                         FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
