package com.ustb.seforge.ai.application;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AiStreamHandle {
    private final AtomicBoolean cancelled;
    private final AtomicReference<Runnable> cancelAction;

    AiStreamHandle(AtomicBoolean cancelled, AtomicReference<Runnable> cancelAction) {
        this.cancelled = cancelled;
        this.cancelAction = cancelAction;
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            Runnable action = cancelAction.get();
            if (action != null) action.run();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    void onCancel(Runnable action) {
        cancelAction.set(action);
        if (cancelled.get()) action.run();
    }
}
