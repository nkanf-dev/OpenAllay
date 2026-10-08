package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import dev.openallay.api.extension.ExtensionInvocation;
import dev.openallay.api.extension.MinecraftWorldAccess;
import dev.openallay.api.extension.WorldSession;
import java.util.Objects;

/** Minecraft 26.2 integrated-server adapter. Construction does not capture a game session. */
public final class Minecraft26WorldAccess implements MinecraftWorldAccess {
    public Minecraft26WorldAccess() {}

    /** Capture only for an active, exact player invocation on its non-owner worker. */
    @Override public WorldSession open(ExtensionInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        invocation.requireActive();
        if (invocation.isCancelled())
            throw new ExtensionException("session_closed", "The world invocation is cancelled");
        if (invocation.callerKind() != ExtensionInvocation.CallerKind.PLAYER
                || invocation.callerUuid() == null || dev.openallay.util.Java8ApiSupport.isEmpty(invocation.playerDimension()))
            throw new ExtensionException("player_required", "An exact local player invocation is required");
        OwnerThreadBridge bridge = new OwnerThreadBridge(invocation::requireActive, NativeWorldBinding::isAnyOwnerThread);
        // The callback retains only revocation state, not a native world session.
        try {
            invocation.onCancel(bridge::close);
            return NativeWorldSession.capture(bridge, invocation);
        } catch (RuntimeException | Error failure) {
            bridge.close();
            throw failure;
        }
    }
}
