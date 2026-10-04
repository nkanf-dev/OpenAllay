package dev.openallay.guide.ui.hud;

import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideSubscription;
import dev.openallay.guide.ui.GuideDisplayConfig;
import java.util.Objects;
import java.util.function.Supplier;

/** Client-tick owner of a passive HUD subscription. Rendering reads only {@link #view()}. */
public final class GuideHudController implements AutoCloseable {
    private final GuideServiceManager services;
    private final Supplier<GuideDisplayConfig> displayConfig;
    private final GuideHudPresenter presenter = new GuideHudPresenter();
    private GuideService subscribedService;
    private TickCoalescer pending;
    private GuideSnapshot latestSnapshot;
    private GuideDisplayConfig lastConfig;
    private GuideDisplayConfig projectedConfig;
    private volatile GuideHudView view;
    private boolean closed;

    public GuideHudController(
            GuideServiceManager services, Supplier<GuideDisplayConfig> displayConfig) {
        this.services = Objects.requireNonNull(services, "services");
        this.displayConfig = Objects.requireNonNull(displayConfig, "displayConfig");
        lastConfig = Objects.requireNonNull(displayConfig.get(), "displayConfig value");
        view = GuideHudView.empty(lastConfig);
    }

    public GuideHudView view() {
        return view;
    }

    /** Never creates a service, fetches history, captures Game state, or sends a request. */
    public void tick() {
        if (closed) return;
        lastConfig = Objects.requireNonNull(displayConfig.get(), "displayConfig value");
        if (!lastConfig.ui().hud().enabled()) {
            disconnect();
            return;
        }
        GuideService current = services.current();
        if (current != subscribedService) {
            disconnect();
            if (current != null) {
                subscribedService = current;
                pending = new TickCoalescer();
                pending.offer(current.snapshot());
                pending.register(current.subscribe(pending::offer));
            }
        }
        GuideSnapshot drained = pending == null ? null : pending.drain();
        if (drained != null) latestSnapshot = drained;
        if (latestSnapshot == null) {
            view = GuideHudView.empty(lastConfig);
        } else if (drained != null || !lastConfig.equals(projectedConfig)) {
            view = presenter.project(latestSnapshot, lastConfig);
            projectedConfig = lastConfig;
        }
    }

    /** Clears only this HUD. The connection's shared GuideService is not disconnected here. */
    public void disconnect() {
        if (pending != null) pending.close();
        pending = null;
        subscribedService = null;
        latestSnapshot = null;
        projectedConfig = null;
        presenter.clear();
        view = GuideHudView.empty(lastConfig);
    }

    @Override
    public void close() {
        closed = true;
        disconnect();
    }

    /** At most one latest immutable snapshot drains each tick, including event bursts. */
    private static final class TickCoalescer implements AutoCloseable {
        private GuideSnapshot latest;
        private GuideSubscription subscription;
        private boolean closed;

        synchronized void register(GuideSubscription value) {
            subscription = Objects.requireNonNull(value, "subscription");
            if (closed) subscription.close();
        }

        synchronized void offer(GuideSnapshot snapshot) {
            if (closed) {
                // GuideService may register on its dispatcher after the initial close.
                if (subscription != null) subscription.close();
                return;
            }
            latest = Objects.requireNonNull(snapshot, "snapshot");
        }

        synchronized GuideSnapshot drain() {
            GuideSnapshot drained = latest;
            latest = null;
            return drained;
        }

        @Override
        public synchronized void close() {
            closed = true;
            latest = null;
            if (subscription != null) subscription.close();
        }
    }
}
