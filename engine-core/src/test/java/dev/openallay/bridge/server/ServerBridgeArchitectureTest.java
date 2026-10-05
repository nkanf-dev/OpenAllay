package dev.openallay.bridge.server;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Structural guard: new server bridge features must edit one native-free owner, not loader families. */
final class ServerBridgeArchitectureTest {
    @Test void serverSessionHasNoGameOrLoaderTypes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/bridge/server/ServerBridgeSession.java"));
        assertFalse(source.contains("net.minecraft"));
        assertFalse(source.contains("net.fabricmc"));
        assertFalse(source.contains("net.neoforged"));
        assertFalse(source.contains("net.minecraftforge"));
        assertTrue(source.contains("ServerAgentService.ContextProvider"));
        assertTrue(source.contains("RemoteToolServer.ContextProvider"));
        assertTrue(source.contains("transport.send(actor, kind, codec.encode(payload))"));
        assertTrue(source.contains("service().hasRequest(actor, requestId)"));
        assertTrue(source.contains("service().ownsRequest(actor, requestId)"));
        assertTrue(source.contains("new dev.openallay.agent.AgentEvent.RequestReleased()"));
    }
    @Test void loaderFacadesDoNotOwnServerProtocolAlgorithms() throws Exception {
        Path repository = Path.of("..");
        for (String relative : java.util.List.of(
                "fabric/src/main/java/dev/openallay/fabric/network/FabricServerBridge.java",
                "neoforge/src/main/java/dev/openallay/neoforge/network/NeoForgeServerBridge.java")) {
            String source = Files.readString(repository.resolve(relative));
            assertTrue(source.contains("ServerBridgeSession"), relative);
            assertFalse(source.contains("switch ("), relative);
            assertFalse(source.contains("ServerAgentRequestChunker"), relative);
            assertFalse(source.contains("ServerGuideRuntime.create"), relative);
            assertTrue(source.contains("players.get(actor) == player"), relative);
        }
    }
}
