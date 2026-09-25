package com.ustb.seforge.ai.application;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AiStreamHandle {
    public enum TerminalState { DONE, ERROR, CANCELLED }
    private TerminalState terminalState;
    private final AtomicBoolean cancelled;
    private final AtomicReference<Runnable> cancelAction;

    AiStreamHandle(AtomicBoolean cancelled, AtomicReference<Runnable> cancelAction) {
        this.cancelled = cancelled;
        this.cancelAction = cancelAction;
    }

    public synchronized void cancel() {
        if (terminalState != null) return;
        terminalState = TerminalState.CANCELLED;
        if (cancelled.compareAndSet(false, true)) {
            Runnable action = cancelAction.get();
            if (action != null) action.run();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public synchronized TerminalState terminalState() { return terminalState; }

    synchronized boolean finish(TerminalState state) {
        if (terminalState != null) return false;
        terminalState = state;
        return true;
    }

    synchronized void onCancel(Runnable action) {
        cancelAction.set(action);
        if (cancelled.get()) action.run();
    }
}
