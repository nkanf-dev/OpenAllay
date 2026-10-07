package dev.openallay.client.voice;

import dev.openallay.net.HttpCancellation;
import java.util.ArrayList;
import java.util.List;

/** Removable voice registration bridges into the canonical HTTP cancellation boundary. */
final class VoiceHttpCancellation implements HttpCancellation {
    private final VoiceCancellation voice;
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean stopped;

    VoiceHttpCancellation(VoiceCancellation voice) { this.voice = voice; }
    @Override public synchronized boolean isCancelled() { return stopped || voice.cancelled(); }
    @Override public void onCancel(Runnable listener) {
        synchronized (this) {
            if (!isCancelled()) { listeners.add(listener); return; }
        }
        listener.run();
    }
    void cancel() {
        List<Runnable> snapshot;
        synchronized (this) {
            if (stopped) return;
            stopped = true;
            snapshot = new ArrayList<>(listeners);
            listeners.clear();
        }
        for (Runnable listener : snapshot) listener.run();
    }
}
