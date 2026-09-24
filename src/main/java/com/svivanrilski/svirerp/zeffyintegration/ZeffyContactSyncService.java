package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class ZeffyContactSyncService {

    private static final Logger log = LoggerFactory.getLogger(ZeffyContactSyncService.class);

    private final ZeffyApiClient apiClient;
    private final ZeffyContactProcessor processor;
    private final ZeffySyncRunService runService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ZeffySyncRun synchronize(String initiatedBy) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalArgumentException("A Zeffy contact synchronization is already running");
        }
        ZeffySyncRun run = null;
        Counts counts = new Counts();
        String cursor = null;
        try {
            run = runService.start("CONTACTS", "MANUAL", initiatedBy);
            Set<String> seenCursors = new HashSet<>();
            while (true) {
                ZeffyApiClient.ContactPageFetch page = apiClient.fetchContactPage(cursor);
                for (JsonNode contact : page.contacts()) {
                    counts.fetched++;
                    try {
                        ZeffyContactProcessor.ApplyResult result = processor.applyFromSync(contact);
                        if (result.inserted()) counts.inserted++;
                        else if (result.updated()) counts.updated++;
                        else counts.ignored++;
                        if (result.needsReview()) counts.needsReview++;
                        else counts.processed++;
                    } catch (RuntimeException ex) {
                        counts.failed++;
                        log.error("Could not synchronize a Zeffy contact in run {}", run.getId(), ex);
                    }
                }
                runService.checkpointContacts(run.getId(), counts.snapshot(), page.nextCursor());
                if (!page.hasMore()) break;
                cursor = page.nextCursor();
                if (!seenCursors.add(cursor)) {
                    throw new ZeffyApiException(502, "Zeffy returned a repeated contact pagination cursor");
                }
            }
            return runService.finishContacts(run.getId(), counts.snapshot(), cursor);
        } catch (RuntimeException ex) {
            if (run != null) {
                try {
                    runService.abortContacts(run.getId(), counts.snapshot(), cursor, safeError(ex));
                } catch (RuntimeException recordingFailure) {
                    ex.addSuppressed(recordingFailure);
                }
            }
            throw ex;
        } finally {
            running.set(false);
        }
    }

    private String safeError(RuntimeException ex) {
        return ex.getMessage() == null ? "Unexpected contact synchronization failure" : ex.getMessage();
    }

    private static final class Counts {
        int fetched;
        int inserted;
        int updated;
        int ignored;
        int failed;
        int needsReview;
        int processed;

        ZeffySyncRunService.ContactCounts snapshot() {
            return new ZeffySyncRunService.ContactCounts(
                    fetched, inserted, updated, ignored, failed, needsReview, processed);
        }
    }
}
