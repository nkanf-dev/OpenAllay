package dev.openallay.bridge.server;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.FeatureServices;
import dev.openallay.bridge.protocol.*;
import dev.openallay.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Native-free extraction checks. No game, model, network or filesystem setup. */
final class ServerBridgeSessionTest {
    @Test void initialCapabilitiesUseCapturedActorAndInitialTransportOnly() {
        Fixture fixture = new Fixture();
        UUID actor = UUID.randomUUID();
        List<Frame> initial = new ArrayList<>();
        fixture.session.connected(actor, (recipient, kind, json) -> {
            initial.add(new Frame(recipient, kind, json));
            return true;
        });
        assertTrue(fixture.sent.isEmpty(), "JOIN's initial sender must not use later channel policy");
        assertEquals(1, initial.size());
        assertEquals(actor, initial.get(0).actor());
        assertEquals("capabilities", initial.get(0).kind());
        CapabilityPayload capabilities = fixture.codec.decode(initial.get(0).json(), CapabilityPayload.class);
        assertFalse(capabilities.serverModel());
        assertTrue(capabilities.remoteTools().isEmpty());
    }

    @Test void unownedSteerChunkRejectsOnlyInitiatingActorAndKeepsCorrelation() {
        Fixture fixture = new Fixture();
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        fixture.session.receive(actor, "agent_steer_chunk", fixture.codec.encode(
                new ServerAgentSteerChunker().split(request, message, "{}", 100).get(0)));
        assertEquals(1, fixture.sent.size());
        Frame frame = fixture.sent.get(0);
        assertEquals(actor, frame.actor());
        assertEquals("agent_event_chunk", frame.kind());
        ServerAgentEventChunkPayload chunk = fixture.codec.decode(frame.json(), ServerAgentEventChunkPayload.class);
        assertEquals(request, chunk.requestId());
        String json = new ResultChunker.Reassembler().accept(chunk.asRemoteChunk()).orElseThrow();
        ServerAgentEventPayload event = fixture.codec.decode(json, ServerAgentEventPayload.class);
        var decoded = new ServerAgentEventCodec(dev.openallay.json.EngineJson.create()).decode(event, request);
        assertEquals(message, ((dev.openallay.agent.AgentEvent.SteerRejected) decoded).messageId());
        assertFalse(event.terminal());
    }

    @Test void malformedUnknownFramesDoNotFabricateReleaseOrGrantAnotherActor() {
        Fixture fixture = new Fixture();
        UUID actor = UUID.randomUUID();
        fixture.session.receive(actor, "agent_request_chunk", "{not-json}");
        fixture.session.receive(actor, "unknown", "{}");
        assertTrue(fixture.sent.isEmpty());
        assertThrows(NullPointerException.class, () -> fixture.session.receive(null, "unknown", "{}"));
        fixture.session.disconnected(actor);
        fixture.session.connected(actor);
        assertEquals(actor, fixture.sent.get(0).actor());
        assertEquals("capabilities", fixture.sent.get(0).kind());
    }

    private record Frame(UUID actor, String kind, String json) {}
    private static final class Fixture {
        final BridgeJsonCodec codec = new BridgeJsonCodec();
        final List<Frame> sent = new ArrayList<>();
        final ToolRegistry tools = new ToolRegistry();
        final FeatureServices runtime = new FeatureServices() {
            @Override public ToolRegistry tools() { return tools; }
            @Override public dev.openallay.skill.SkillRepository skills() { throw new AssertionError(); }
            @Override public dev.openallay.platform.PlatformService platform() { throw new AssertionError(); }
            @Override public dev.openallay.script.command.CommandCapabilityRuntime commands() { throw new AssertionError(); }
            @Override public dev.openallay.extension.OpenAllayExtensionRegistry extensions() { throw new AssertionError(); }
            @Override public dev.openallay.script.extension.JavascriptDataModuleRegistry javascriptModules() { throw new AssertionError(); }
            @Override public dev.openallay.knowledge.KnowledgeRegistry knowledge() { throw new AssertionError(); }
            @Override public dev.openallay.capability.CapabilitySettingsCatalog capabilitySettings() { throw new AssertionError(); }
        };
        final ServerBridgeSession session = new ServerBridgeSession(runtime, (actor, kind, json) -> {
            sent.add(new Frame(actor, kind, json));
            return true;
        });
    }
}
