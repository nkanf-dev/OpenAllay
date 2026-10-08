package dev.openallay.client.voice;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executor;

/** Native client composition. Construction does not open capture or discover credentials. */
public final class VoiceClientRuntimes {
    private VoiceClientRuntimes() {}

    public static VoiceClientRuntime create(Path configDirectory, VoiceRuntime.DraftPort drafts, Executor clientDispatcher) {
        return create(configDirectory, drafts, clientDispatcher, dev.openallay.util.Java8Collections.mapOf());
    }

    /** Environment is explicitly supplied by the existing client boundary; never read it here. */
    public static VoiceClientRuntime create(Path configDirectory, VoiceRuntime.DraftPort drafts,
            Executor clientDispatcher, Map<String, String> credentialEnvironment) {
        return new VoiceClientRuntime(configDirectory, drafts, clientDispatcher,
                dev.openallay.util.Java8Collections.mapCopyOf(credentialEnvironment), new OpenAlCapture());
    }
}
