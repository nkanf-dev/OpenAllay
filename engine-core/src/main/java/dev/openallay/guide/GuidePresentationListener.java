package dev.openallay.guide;

import java.util.UUID;

/** Installed on the manager before request admission; no snapshots or durable history replay. */
public interface GuidePresentationListener {
    void bound(GuideService service);
    void event(GuidePresentationEvent event);
    void invalidated(UUID connectionGeneration);
}
