package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionIdentityTest {
    private final Object connection = new Object(), clientPlayer = new Object(), clientLevel = new Object();
    private final Object server = new Object(), serverPlayer = new Object(), serverLevel = new Object();
    private final UUID actor = new UUID(42, 1);
    private final String dimension = "minecraft:overworld";
    private SessionIdentity identity() {
        SessionIdentity identity = new SessionIdentity(connection,clientPlayer,clientLevel,server,actor,dimension);
        identity.bindServer(serverPlayer,serverLevel);
        return identity;
    }

    @Test void exactReferencesAndValuesAreAccepted() {
        SessionIdentity identity = identity();
        identity.requireClient(connection,clientPlayer,clientLevel,server,new UUID(42,1),new String(dimension));
        identity.requireServer(serverPlayer,serverLevel,new UUID(42,1),new String(dimension));
    }
    @Test void sameUuidReconnectAndClientLevelReplacementAreRejected() {
        SessionIdentity identity = identity();
        assertStale(() -> identity.requireClient(new Object(),clientPlayer,clientLevel,server,actor,dimension));
        assertStale(() -> identity.requireClient(connection,new Object(),clientLevel,server,actor,dimension));
        assertStale(() -> identity.requireClient(connection,clientPlayer,new Object(),server,actor,dimension));
        assertStale(() -> identity.requireClient(connection,clientPlayer,clientLevel,new Object(),actor,dimension));
    }
    @Test void actorDimensionAndServerPlayerLevelChangesAreRejected() {
        SessionIdentity identity = identity();
        assertStale(() -> identity.requireClient(connection,clientPlayer,clientLevel,server,UUID.randomUUID(),dimension));
        assertStale(() -> identity.requireClient(connection,clientPlayer,clientLevel,server,actor,"minecraft:the_nether"));
        assertStale(() -> identity.requireServer(new Object(),serverLevel,actor,dimension));
        assertStale(() -> identity.requireServer(serverPlayer,new Object(),actor,dimension));
        assertStale(() -> identity.requireServer(serverPlayer,serverLevel,UUID.randomUUID(),dimension));
        assertStale(() -> identity.requireServer(serverPlayer,serverLevel,actor,"minecraft:the_nether"));
    }
    @Test void unboundOrRepeatedServerCaptureNeverBecomesImplicitRebind() {
        SessionIdentity identity = new SessionIdentity(connection,clientPlayer,clientLevel,server,actor,dimension);
        assertStale(() -> identity.requireServer(serverPlayer,serverLevel,actor,dimension));
        identity.bindServer(serverPlayer,serverLevel);
        assertThrows(IllegalStateException.class, () -> identity.bindServer(new Object(),new Object()));
    }
    private static void assertStale(Runnable action) {
        assertEquals("stale_session", assertThrows(ExtensionException.class,action::run).code());
    }
}
