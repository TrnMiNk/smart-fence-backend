package smart_fence_backend.mqtt;

import org.junit.jupiter.api.Test;
import smart_fence_backend.dto.DeviceCommandAckPayload;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PendingSystemAckRegistryTests {
    @Test
    void successArrivingBeforeAbandonIsRetainedAtomically() {
        PendingSystemAckRegistry registry = new PendingSystemAckRegistry();
        registry.beginDispatch(7L);
        assertEquals(PendingSystemAckRegistry.DeferResult.DEFERRED,
                registry.deferIfDispatching(7L, "D", ack("SUCCESS")));
        var result = registry.abandonDispatch(7L);
        assertFalse(result.abandoned());
        assertEquals(1, result.retainedAcks());
        AtomicInteger processed = new AtomicInteger();
        registry.setAckProcessor((device, ack) -> processed.incrementAndGet());
        registry.completeDispatch(7L);
        assertEquals(1, processed.get());
    }

    @Test
    void failedProcessorKeepsAckForBoundedExplicitRetry() {
        PendingSystemAckRegistry registry = new PendingSystemAckRegistry();
        registry.beginDispatch(8L);
        registry.deferIfDispatching(8L, "D", ack("SUCCESS"));
        AtomicInteger processed = new AtomicInteger();
        registry.setAckProcessor((device, ack) -> {
            if (processed.incrementAndGet() < 2) throw new IllegalStateException("transient db failure");
        });
        registry.completeDispatch(8L);
        assertEquals(2, processed.get());
    }

    @Test
    void ackRacingCompleteIsEitherDeferredOrObservedAfterDispatchCloses() throws Exception {
        PendingSystemAckRegistry registry = new PendingSystemAckRegistry();
        registry.beginDispatch(10L);
        registry.deferIfDispatching(10L, "D", ack("FAILED"));
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        registry.setAckProcessor((device, ack) -> {
            processing.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        Thread completing = new Thread(() -> registry.completeDispatch(10L));
        completing.start();
        assertTrue(processing.await(2, TimeUnit.SECONDS));
        AtomicReference<PendingSystemAckRegistry.DeferResult> result = new AtomicReference<>();
        Thread ackArrival = new Thread(() -> result.set(
                registry.deferIfDispatching(10L, "D", ack("SUCCESS"))));
        ackArrival.start();
        release.countDown();
        completing.join(2000);
        ackArrival.join(2000);
        assertFalse(completing.isAlive());
        assertFalse(ackArrival.isAlive());
        assertEquals(PendingSystemAckRegistry.DeferResult.NO_DISPATCH, result.get());
    }

    @Test
    void fullBufferNeverProcessesAckInline() {
        PendingSystemAckRegistry registry = new PendingSystemAckRegistry();
        registry.beginDispatch(9L);
        AtomicInteger processed = new AtomicInteger();
        registry.setAckProcessor((device, ack) -> processed.incrementAndGet());
        for (int i = 0; i < 16; i++) {
            assertEquals(PendingSystemAckRegistry.DeferResult.DEFERRED,
                    registry.deferIfDispatching(9L, "D", ack("SUCCESS")));
        }
        assertEquals(PendingSystemAckRegistry.DeferResult.DISPATCH_FULL,
                registry.deferIfDispatching(9L, "D", ack("SUCCESS")));
        assertEquals(0, processed.get());
        registry.completeDispatch(9L);
        assertEquals(16, processed.get());
    }

    private DeviceCommandAckPayload ack(String status) {
        DeviceCommandAckPayload ack = new DeviceCommandAckPayload();
        ack.setStatus(status);
        return ack;
    }
}
