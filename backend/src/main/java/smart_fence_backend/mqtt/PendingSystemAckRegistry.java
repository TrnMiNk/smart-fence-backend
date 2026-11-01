package smart_fence_backend.mqtt;

import smart_fence_backend.dto.DeviceCommandAckPayload;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/** Buffers ACKs that arrive while a just-published command is being marked SENT. */
@Component
public class PendingSystemAckRegistry {

    private static final int MAX_ACKS_PER_COMMAND = 16;
    private static final int MAX_PROCESS_ATTEMPTS = 3;

    private final ConcurrentMap<Long, Dispatch> dispatches = new ConcurrentHashMap<>();
    private volatile BiConsumer<String, DeviceCommandAckPayload> ackProcessor;

    public enum DeferResult {
        NO_DISPATCH,
        DEFERRED,
        DISPATCH_FULL
    }

    public void setAckProcessor(BiConsumer<String, DeviceCommandAckPayload> processor) {
        this.ackProcessor = processor;
    }

    public void beginDispatch(Long commandId) {
        if (dispatches.putIfAbsent(commandId, new Dispatch()) != null) {
            throw new IllegalStateException("Command dispatch is already active!");
        }
    }

    public DeferResult deferIfDispatching(
            Long commandId, String deviceCode, DeviceCommandAckPayload ack) {
        Dispatch dispatch = dispatches.get(commandId);
        if (dispatch == null) {
            return DeferResult.NO_DISPATCH;
        }
        synchronized (dispatch) {
            if (dispatches.get(commandId) != dispatch) {
                return DeferResult.NO_DISPATCH;
            }
            if (dispatch.acks.size() >= MAX_ACKS_PER_COMMAND) {
                if ("SUCCESS".equalsIgnoreCase(ack.getStatus())) {
                    for (int index = 0; index < dispatch.acks.size(); index++) {
                        if ("FAILED".equalsIgnoreCase(
                                dispatch.acks.get(index).payload().getStatus())) {
                            dispatch.acks.set(index, new DeferredAck(deviceCode, ack));
                            return DeferResult.DEFERRED;
                        }
                    }
                }
                return DeferResult.DISPATCH_FULL;
            }
            dispatch.acks.add(new DeferredAck(deviceCode, ack));
            return DeferResult.DEFERRED;
        }
    }

    /** Atomically abandons only if no SUCCESS ACK is waiting. */
    public AbandonResult abandonDispatch(Long commandId) {
        return abandonDispatch(commandId, () -> { });
    }

    /** Checks buffered ACKs, commits the terminal DB transition, and closes atomically
     * with respect to ACK arrival for this command. */
    public AbandonResult abandonDispatch(Long commandId, Runnable terminalCommit) {
        Dispatch dispatch = dispatches.get(commandId);
        if (dispatch == null) {
            terminalCommit.run();
            return new AbandonResult(true, 0);
        }
        synchronized (dispatch) {
            if (dispatches.get(commandId) != dispatch) {
                return new AbandonResult(true, 0);
            }
            if (dispatch.acks.stream().anyMatch(ack ->
                    "SUCCESS".equalsIgnoreCase(ack.payload().getStatus()))) {
                return new AbandonResult(false, dispatch.acks.size());
            }
            terminalCommit.run();
            dispatches.remove(commandId, dispatch);
            int discarded = dispatch.acks.size();
            dispatch.acks.clear();
            return new AbandonResult(true, discarded);
        }
    }

    /** Releases a dispatch only after its database command has reached a terminal state. */
    public void discardTerminalDispatch(Long commandId) {
        Dispatch dispatch = dispatches.get(commandId);
        if (dispatch == null) return;
        synchronized (dispatch) {
            dispatch.acks.clear();
            dispatches.remove(commandId, dispatch);
        }
    }

    public boolean hasSuccessfulAck(Long commandId) {
        Dispatch dispatch = dispatches.get(commandId);
        if (dispatch == null) {
            return false;
        }
        synchronized (dispatch) {
            return dispatch.acks.stream().anyMatch(ack ->
                    "SUCCESS".equalsIgnoreCase(ack.payload().getStatus()));
        }
    }

    public void completeDispatch(Long commandId) {
        Dispatch dispatch = dispatches.get(commandId);
        if (dispatch == null) {
            return;
        }

        List<DeferredAck> pending;
        synchronized (dispatch) {
            pending = List.copyOf(dispatch.acks);
            BiConsumer<String, DeviceCommandAckPayload> processor = ackProcessor;
            if (processor == null) return;
            int completed = 0;
            try {
                for (DeferredAck ack : pending) {
                    RuntimeException lastFailure = null;
                    for (int attempt = 0; attempt < MAX_PROCESS_ATTEMPTS; attempt++) {
                        try {
                            processor.accept(ack.deviceCode(), ack.payload());
                            lastFailure = null;
                            break;
                        } catch (RuntimeException failure) {
                            lastFailure = failure;
                        }
                    }
                    if (lastFailure != null) throw lastFailure;
                    completed++;
                }
                dispatch.acks.clear();
                dispatches.remove(commandId, dispatch);
            } catch (RuntimeException failure) {
                // Keep the failed ACK and remainder available for an explicit retry.
                dispatch.acks.subList(0, completed).clear();
                throw failure;
            }
        }
    }

    public record AbandonResult(boolean abandoned, int retainedAcks) { }

    private static final class Dispatch {
        private final List<DeferredAck> acks = new ArrayList<>();
    }

    private record DeferredAck(String deviceCode, DeviceCommandAckPayload payload) { }
}
