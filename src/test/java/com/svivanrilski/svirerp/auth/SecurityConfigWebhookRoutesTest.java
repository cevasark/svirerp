package com.svivanrilski.svirerp.auth;

import com.svivanrilski.svirerp.settings.AppSettingService;
import com.svivanrilski.svirerp.stripeintegration.StripeWebhookController;
import com.svivanrilski.svirerp.stripeintegration.StripeWebhookService;
import com.svivanrilski.svirerp.zeffyintegration.ZeffyWebhookController;
import com.svivanrilski.svirerp.zeffyintegration.ZeffyWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ZeffyWebhookController.class, StripeWebhookController.class})
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, LocalAdminSecurityConfig.class})
@TestPropertySource(properties = {
        "app.auth.admin.username=",
        "app.auth.admin.password-hash=",
        "app.auth.google.allowed-domain=example.org",
        "debug=false",
        "logging.level.root=WARN"
})
class SecurityConfigWebhookRoutesTest {

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockitoBean
    private ZeffyWebhookService zeffyWebhookService;

    @MockitoBean
    private StripeWebhookService stripeWebhookService;

    @MockitoBean
    private GoogleHostedDomainOidcUserService googleHostedDomainOidcUserService;

    @MockitoBean
    private LoginAttemptService loginAttemptService;

    @MockitoBean
    private AppSettingService appSettingService;

    @BeforeEach
    void setUp() {
        when(appSettingService.getDecryptedValue(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void exactZeffyAndStripeWebhookRoutesAllowAnonymousPostsWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/webhooks/zeffy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void otherWebhookPathsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/webhooks/unexpected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void otherWebhookPostsStillRequireCsrf() throws Exception {
        mockMvc.perform(post("/api/webhooks/unexpected"))
                .andExpect(status().isForbidden());
    }
}
