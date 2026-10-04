package dev.openallay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.agent.context.ContextBudget;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.guide.GuideContextSpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ServerModelCapabilityProjectionTest {
    @Test
    void advertisesOnlyACompleteRuntimeDescriptor() {
        CapabilityPayload absent =
                ServerModelCapabilityProjection.from(List.of(), Optional.empty());
        assertFalse(absent.serverModel());
        assertEquals("", absent.serverCanonicalModelId());

        CapabilityPayload present = ServerModelCapabilityProjection.from(
                List.of(),
                Optional.of(new GuideContextSpec(
                        new ContextBudget(100_000, 8_192),
                        6_000,
                        "server/deepseek")));
        assertTrue(present.serverModel());
        assertEquals("server/deepseek", present.serverCanonicalModelId());
        assertEquals(100_000, present.serverContextWindowTokens());
    }
}
