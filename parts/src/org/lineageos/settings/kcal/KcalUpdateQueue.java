// SPDX-License-Identifier: Apache-2.0
package org.lineageos.settings.kcal;

import java.io.IOException;
import java.util.concurrent.Executor;

/** At most one running write and one latest pending profile, never a slider backlog. */
final class KcalUpdateQueue {
    interface Applier {
        void apply(boolean enabled, int[] profile, boolean persist) throws IOException;
    }

    interface Callback {
        void complete(IOException error);
    }

    private static final class Request {
        final boolean enabled;
        final int[] profile;
        final boolean persist;
        final Callback callback;

        Request(boolean enabled, int[] profile, boolean persist, Callback callback) {
            this.enabled = enabled;
            this.profile = profile.clone();
            this.persist = persist;
            this.callback = callback;
        }
    }

    private final Executor executor;
    private final Applier applier;
    private Request pending;
    private boolean running;

    KcalUpdateQueue(Executor executor, Applier applier) {
        this.executor = executor;
        this.applier = applier;
    }

    synchronized void submit(boolean enabled, int[] profile, boolean persist, Callback callback) {
        // Preserve a pending save when a more recent preview replaces it.
        pending = new Request(enabled, profile, persist || (pending != null && pending.persist),
                callback);
        if (!running) {
            running = true;
            executor.execute(this::drain);
        }
    }

    private void drain() {
        while (true) {
            Request request;
            synchronized (this) {
                request = pending;
                pending = null;
                if (request == null) {
                    running = false;
                    return;
                }
            }
            IOException error = null;
            try {
                applier.apply(request.enabled, request.profile, request.persist);
            } catch (IOException e) {
                error = e;
            }
            request.callback.complete(error);
        }
    }
}
