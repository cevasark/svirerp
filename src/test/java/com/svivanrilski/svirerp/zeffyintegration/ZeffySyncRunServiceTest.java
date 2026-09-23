package com.svivanrilski.svirerp.zeffyintegration;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZeffySyncRunServiceTest {

    @Test
    void previewCompletesWhenBusinessReviewItemsWereSuccessfullyClassified() {
        ZeffySyncRun run = finish("PREVIEW", new ZeffySyncRunService.PaymentCounts(
                2, 2, 0, 0, 0, 0, 1, 0, 1, 0));

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void applyIsPartialWhilePaymentsStillNeedMapping() {
        ZeffySyncRun run = finish("APPLY", new ZeffySyncRunService.PaymentCounts(
                2, 0, 2, 0, 0, 0, 0, 1, 0, 1));

        assertThat(run.getStatus()).isEqualTo("PARTIAL");
    }

    private ZeffySyncRun finish(String mode, ZeffySyncRunService.PaymentCounts counts) {
        UUID id = UUID.randomUUID();
        ZeffySyncRunRepository repository = mock(ZeffySyncRunRepository.class);
        ZeffySyncRun run = ZeffySyncRun.builder().id(id).executionMode(mode).status("RUNNING").build();
        when(repository.findById(id)).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        return new ZeffySyncRunService(repository).finishPayment(id, counts, null);
    }
}
