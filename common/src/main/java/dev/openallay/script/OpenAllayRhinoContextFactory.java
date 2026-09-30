package dev.openallay.script;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.openallay.model.CancellationSignal;
import java.time.Duration;
import java.util.Objects;

final class OpenAllayRhinoContextFactory extends ContextFactory {
    private final CancellationSignal cancellation;
    private final Duration timeout;
    private final boolean unrestricted;

    OpenAllayRhinoContextFactory(CancellationSignal cancellation, Duration timeout, boolean unrestricted) {
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.unrestricted = unrestricted;
        setInstanceStaticFallback(false);
    }

    @Override
    protected Context createContext() {
        return new OpenAllayRhinoContext(this, cancellation, timeout, unrestricted);
    }
}

