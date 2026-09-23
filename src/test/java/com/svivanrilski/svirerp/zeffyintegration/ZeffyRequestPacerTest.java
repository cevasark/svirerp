package com.svivanrilski.svirerp.zeffyintegration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ZeffyRequestPacerTest {

    @Test
    void spacesEveryRequestStartByAtLeastOneSecond() {
        AtomicLong clock = new AtomicLong();
        List<Long> sleeps = new ArrayList<>();
        ZeffyRequestPacer pacer = new ZeffyRequestPacer(clock::get, nanos -> {
            sleeps.add(nanos);
            clock.addAndGet(nanos);
        });

        pacer.awaitPermit();
        pacer.awaitPermit();
        clock.addAndGet(ZeffyRequestPacer.INTERVAL_NANOS / 4);
        pacer.awaitPermit();

        assertThat(sleeps).containsExactly(
                ZeffyRequestPacer.INTERVAL_NANOS,
                ZeffyRequestPacer.INTERVAL_NANOS * 3 / 4);
    }
}
