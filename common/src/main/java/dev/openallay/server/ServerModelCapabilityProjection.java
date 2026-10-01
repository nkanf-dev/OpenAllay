package dev.openallay.server;

import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.guide.GuideContextSpec;
import java.util.List;
import java.util.Optional;

/** Builds the wire advertisement only from a fully constructed server model runtime. */
public final class ServerModelCapabilityProjection {
    private ServerModelCapabilityProjection() {}

    public static CapabilityPayload from(
            List<CapabilityPayload.RemoteToolCapability> tools,
            Optional<GuideContextSpec> serverModel) {
        List<CapabilityPayload.RemoteToolCapability> detached = List.copyOf(tools);
        if (serverModel.isEmpty()) {
            return new CapabilityPayload(
                    detached, false, 0, 0, 0, "");
        }
        GuideContextSpec spec = serverModel.orElseThrow();
        return new CapabilityPayload(
                detached,
                true,
                spec.budget().contextWindowTokens(),
                spec.budget().maxOutputTokens(),
                spec.promptAndToolTokens(),
                spec.canonicalModelId());
    }
}
