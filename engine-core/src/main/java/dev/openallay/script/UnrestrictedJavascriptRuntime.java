package dev.openallay.script;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Client-owned, request-frozen permission. Never advertised through server Tool callbacks. */
public final class UnrestrictedJavascriptRuntime {
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final ConcurrentMap<String, Boolean> requests = new ConcurrentHashMap<>();
    public boolean enabled() { return enabled.get(); }
    public void replace(UnrestrictedJavascriptConfig config) { enabled.set(config.enabled()); }
    public boolean freeze(String correlationId) { return requests.computeIfAbsent(correlationId, ignored -> enabled.get()); }
    public boolean enabledFor(String correlationId) { return requests.getOrDefault(correlationId, false); }
    public void freezeDisabled(String correlationId) { requests.putIfAbsent(correlationId, false); }
    public void close(String correlationId) { requests.remove(correlationId); }
    public void clear() { requests.clear(); }
}
