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
        return from(tools, serverModel, dev.openallay.model.metadata.ModelImageCapabilityResolution.unknown());
    }

    public static CapabilityPayload from(
            List<CapabilityPayload.RemoteToolCapability> tools,
            Optional<GuideContextSpec> serverModel,
            dev.openallay.model.metadata.ModelImageCapabilityResolution imageCapability) {
        java.util.Objects.requireNonNull(imageCapability, "imageCapability");
        List<CapabilityPayload.RemoteToolCapability> detached = dev.openallay.util.Java8Collections.listCopyOf(tools);
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
                spec.canonicalModelId(),
                imageCapability.capability(),
                imageCapability.origin().name().toLowerCase(java.util.Locale.ROOT)
                        + (imageCapability.source() == null ? "" : ":" + imageCapability.source()));
    }
}
