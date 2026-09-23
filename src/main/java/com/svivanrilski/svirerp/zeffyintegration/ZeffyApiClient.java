package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.svivanrilski.svirerp.settings.AppSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ZeffyApiClient {

    private static final String API_KEY_SETTING = "zeffy.api-key";
    private static final int MAX_ATTEMPTS = 3;

    private final AppSettingService settingService;
    private final ZeffyRequestPacer pacer;
    private final RestClient restClient;

    public record CampaignFetch(List<ZeffyApiModels.Campaign> campaigns, String endingCursor) {
    }

    public record PaymentPageFetch(List<JsonNode> payments,
                                   boolean hasMore, String nextCursor) {
    }

    @Autowired
    public ZeffyApiClient(AppSettingService settingService, ZeffyRequestPacer pacer,
                          RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.settingService = settingService;
        this.pacer = pacer;
        this.restClient = builder.baseUrl("https://api.zeffy.com")
                .requestFactory(requestFactory)
                .build();
    }

    ZeffyApiClient(AppSettingService settingService, ZeffyRequestPacer pacer, RestClient restClient) {
        this.settingService = settingService;
        this.pacer = pacer;
        this.restClient = restClient;
    }

    public void testConnection() {
        testConnection(requireConfiguredApiKey());
    }

    public void testConnection(String apiKey) {
        requireNonBlank(apiKey);
        getCampaignPage(apiKey, null, 1);
    }

    public CampaignFetch fetchAllCampaigns() {
        return fetchAllCampaigns(requireConfiguredApiKey());
    }

    CampaignFetch fetchAllCampaigns(String apiKey) {
        requireNonBlank(apiKey);
        List<ZeffyApiModels.Campaign> campaigns = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        String endingCursor = null;
        while (true) {
            ZeffyApiModels.CampaignPage page = getCampaignPage(apiKey, cursor, 100);
            if (page.data() != null) campaigns.addAll(page.data());
            if (!page.hasMore()) break;
            cursor = page.cursorText();
            if (cursor == null || cursor.isBlank() || !seenCursors.add(cursor)) {
                throw new ZeffyApiException(502, "Zeffy returned an invalid campaign pagination cursor");
            }
            endingCursor = cursor;
        }
        return new CampaignFetch(List.copyOf(campaigns), endingCursor);
    }

    public PaymentPageFetch fetchPaymentPage(long createdFrom, Long createdThrough, String cursor) {
        String apiKey = requireConfiguredApiKey();
        ZeffyApiModels.PaymentPage page = getPaymentPage(apiKey, createdFrom, createdThrough, cursor, 100);
        List<JsonNode> payments = page.data() == null
                ? List.of() : List.copyOf(page.data());
        String nextCursor = page.cursorText();
        if (page.hasMore() && (nextCursor == null || nextCursor.isBlank())) {
            throw new ZeffyApiException(502, "Zeffy returned an invalid payment pagination cursor");
        }
        return new PaymentPageFetch(payments, page.hasMore(), nextCursor);
    }

    private ZeffyApiModels.CampaignPage getCampaignPage(String apiKey, String cursor, int limit) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            pacer.awaitPermit();
            try {
                ZeffyApiModels.CampaignPage page = restClient.get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path("/api/v1/campaigns").queryParam("limit", limit);
                            if (cursor != null) builder.queryParam("starting_after", cursor);
                            return builder.build();
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .retrieve()
                        .body(ZeffyApiModels.CampaignPage.class);
                if (page == null) {
                    throw new ZeffyApiException(502, "Zeffy returned an empty campaign response");
                }
                return page;
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                if (status == 401) {
                    throw new ZeffyApiException(401, "Zeffy rejected the API key");
                }
                if (status == 429) {
                    Duration delay = retryAfter(ex.getResponseHeaders());
                    pacer.defer(delay);
                    if (attempt == MAX_ATTEMPTS) {
                        throw new ZeffyApiException(429,
                                "Zeffy rate limit reached; retry after " + delay.toSeconds() + " seconds", delay);
                    }
                    lastFailure = ex;
                    continue;
                }
                if (status < 500 || attempt == MAX_ATTEMPTS) {
                    throw new ZeffyApiException(502, "Zeffy API request failed", null, ex);
                }
                lastFailure = ex;
            } catch (ResourceAccessException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ZeffyApiException(502, "Could not reach the Zeffy API", null, ex);
                }
                lastFailure = ex;
            }
        }
        throw new ZeffyApiException(502, "Zeffy API request failed", null, lastFailure);
    }

    private ZeffyApiModels.PaymentPage getPaymentPage(String apiKey, long createdFrom,
                                                       Long createdThrough, String cursor, int limit) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            pacer.awaitPermit();
            try {
                ZeffyApiModels.PaymentPage page = restClient.get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path("/api/v1/payments")
                                    .queryParam("status", "succeeded")
                                    .queryParam("created[gte]", createdFrom)
                                    .queryParam("limit", limit);
                            if (createdThrough != null) {
                                builder.queryParam("created[lte]", createdThrough);
                            }
                            if (cursor != null) builder.queryParam("starting_after", cursor);
                            return builder.build();
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .retrieve()
                        .body(ZeffyApiModels.PaymentPage.class);
                if (page == null) {
                    throw new ZeffyApiException(502, "Zeffy returned an empty payment response");
                }
                return page;
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                if (status == 401) throw new ZeffyApiException(401, "Zeffy rejected the API key");
                if (status == 429) {
                    Duration delay = retryAfter(ex.getResponseHeaders());
                    pacer.defer(delay);
                    if (attempt == MAX_ATTEMPTS) {
                        throw new ZeffyApiException(429,
                                "Zeffy rate limit reached; retry after " + delay.toSeconds() + " seconds", delay);
                    }
                    lastFailure = ex;
                    continue;
                }
                if (status < 500 || attempt == MAX_ATTEMPTS) {
                    throw new ZeffyApiException(502, "Zeffy API request failed", null, ex);
                }
                lastFailure = ex;
            } catch (ResourceAccessException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ZeffyApiException(502, "Could not reach the Zeffy API", null, ex);
                }
                lastFailure = ex;
            }
        }
        throw new ZeffyApiException(502, "Zeffy API request failed", null, lastFailure);
    }

    private String requireConfiguredApiKey() {
        return settingService.getDecryptedValue(API_KEY_SETTING)
                .orElseThrow(() -> new IllegalArgumentException("Configure the Zeffy API key first"));
    }

    private void requireNonBlank(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Zeffy API key is required");
        }
    }

    private Duration retryAfter(HttpHeaders headers) {
        String value = headers != null ? headers.getFirst(HttpHeaders.RETRY_AFTER) : null;
        try {
            long seconds = value == null ? 60 : Long.parseLong(value);
            return Duration.ofSeconds(Math.max(1, seconds));
        } catch (NumberFormatException ex) {
            return Duration.ofSeconds(60);
        }
    }
}
