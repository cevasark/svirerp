package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.settings.AppSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ZeffyApiClientTest {

    @Test
    void springCreatesClientUsingItsProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(AppSettingService.class, () -> mock(AppSettingService.class))
                .withBean(ZeffyRequestPacer.class, () -> mock(ZeffyRequestPacer.class))
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withBean(ZeffyApiClient.class)
                .run(context -> assertThat(context).hasSingleBean(ZeffyApiClient.class));
    }

    @Test
    void fetchesEveryCampaignPageWithBearerAuthAndPacing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.zeffy.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZeffyRequestPacer pacer = mock(ZeffyRequestPacer.class);
        ZeffyApiClient client = new ZeffyApiClient(mock(AppSettingService.class), pacer, builder.build());

        server.expect(requestTo("https://api.zeffy.test/api/v1/campaigns?limit=100"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"c1","type":"donation_form","title":"General"}],
                         "has_more":true,"next_cursor":"cursor-1"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://api.zeffy.test/api/v1/campaigns?limit=100&starting_after=cursor-1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"c2","type":"ticketing","title":"Festival"}],
                         "has_more":false,"next_cursor":null}
                        """, MediaType.APPLICATION_JSON));

        ZeffyApiClient.CampaignFetch result = client.fetchAllCampaigns("test-key");

        assertThat(result.campaigns()).extracting(ZeffyApiModels.Campaign::id)
                .containsExactly("c1", "c2");
        assertThat(result.endingCursor()).isEqualTo("cursor-1");
        verify(pacer, times(2)).awaitPermit();
        server.verify();
    }

    @Test
    void rateLimitDefersSharedPacerAndRetriesWithinBound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.zeffy.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZeffyRequestPacer pacer = mock(ZeffyRequestPacer.class);
        ZeffyApiClient client = new ZeffyApiClient(mock(AppSettingService.class), pacer, builder.build());

        server.expect(requestTo("https://api.zeffy.test/api/v1/campaigns?limit=100"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "5"));
        server.expect(requestTo("https://api.zeffy.test/api/v1/campaigns?limit=100"))
                .andRespond(withSuccess("{\"data\":[],\"has_more\":false,\"next_cursor\":null}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.fetchAllCampaigns("test-key").campaigns()).isEmpty();
        verify(pacer).defer(Duration.ofSeconds(5));
        verify(pacer, times(2)).awaitPermit();
        server.verify();
    }

    @Test
    void fetchesSucceededPaymentPageForTheRequestedRangeAndCursor() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.zeffy.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZeffyRequestPacer pacer = mock(ZeffyRequestPacer.class);
        AppSettingService settings = mock(AppSettingService.class);
        org.mockito.Mockito.when(settings.getDecryptedValue("zeffy.api-key"))
                .thenReturn(Optional.of("test-key"));
        ZeffyApiClient client = new ZeffyApiClient(settings, pacer, builder.build());

        server.expect(requestTo("https://api.zeffy.test/api/v1/payments?status=succeeded"
                        + "&created%5Bgte%5D=100&limit=100&created%5Blte%5D=200&starting_after=next-1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"pay-1","status":"succeeded"}],
                         "has_more":false,"next_cursor":null}
                        """, MediaType.APPLICATION_JSON));

        ZeffyApiClient.PaymentPageFetch result = client.fetchPaymentPage(100, 200L, "next-1");

        assertThat(result.payments()).extracting(node -> node.get("id").asText())
                .containsExactly("pay-1");
        assertThat(result.hasMore()).isFalse();
        verify(pacer).awaitPermit();
        server.verify();
    }
}
