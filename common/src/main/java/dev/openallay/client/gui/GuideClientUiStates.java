package dev.openallay.client.gui;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.gui.clipboard.SystemImageClipboard;
import dev.openallay.guide.GuideService;

/** Native clipboard/worker composition for the shared connection draft owner. */
public final class GuideClientUiStates {
    private GuideClientUiStates() {}

    public static GuideClientUiState create(GuideService service, ClientEventDispatcher client) {
        return new GuideClientUiState(service, new SystemImageClipboard(),
                job -> dev.openallay.concurrent.NamedThreads.startDaemon("openallay-draft-image", job), client);
    }
}
