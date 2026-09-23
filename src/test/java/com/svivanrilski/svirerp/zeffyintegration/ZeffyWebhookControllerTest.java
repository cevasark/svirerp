package com.svivanrilski.svirerp.zeffyintegration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ZeffyWebhookControllerTest {

    private final ZeffyWebhookService service = mock(ZeffyWebhookService.class);
    private final ZeffyWebhookController controller = new ZeffyWebhookController(service);

    @Test
    void passesExactRequestBytesToService() throws Exception {
        byte[] payload = "{ \"spacing\": true }".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload);

        assertThat(controller.receive(request, "t=1,v1=abc").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).receive(payload, "t=1,v1=abc");
    }

    @Test
    void rejectsPayloadOverOneMegabyte() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[ZeffyWebhookController.MAX_PAYLOAD_BYTES + 1]);

        assertThatThrownBy(() -> controller.receive(request, "signature"))
                .isInstanceOf(ZeffyWebhookException.class)
                .satisfies(ex -> assertThat(((ZeffyWebhookException) ex).getStatus())
                        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }
}
