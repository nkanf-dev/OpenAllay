package dev.openallay.context;

import dev.openallay.context.game.ObservableGameStateSnapshot;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@dev.openallay.value.ValueType(ToolInvocationContext.ValueSchemaProvider.class)
public final class ToolInvocationContext {
    private final String correlationId;
    private final Instant capturedAt;
    private final CallerSnapshot caller;
    private final Optional<PlayerSnapshot> player;
    private final Optional<RegistrySnapshot> registries;
    private final Optional<RecipeSnapshot> recipes;
    private final Optional<ObservableGameStateSnapshot> observableGameState;
    private final ContextMetrics metrics;
    private final boolean unrestrictedJavascript;
    public ToolInvocationContext(String correlationId, Instant capturedAt, CallerSnapshot caller, Optional<PlayerSnapshot> player, Optional<RegistrySnapshot> registries, Optional<RecipeSnapshot> recipes, Optional<ObservableGameStateSnapshot> observableGameState, ContextMetrics metrics, boolean unrestrictedJavascript) {

        correlationId = ContextValidation.nonBlank(correlationId, "correlationId");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(caller, "caller");
        player = Objects.requireNonNull(player, "player");
        registries = Objects.requireNonNull(registries, "registries");
        recipes = Objects.requireNonNull(recipes, "recipes");
        observableGameState = Objects.requireNonNull(observableGameState, "observableGameState");
        Objects.requireNonNull(metrics, "metrics");
        if (caller.kind() == CallerKind.PLAYER && player.isPresent()
                && !caller.uuid().equals(player.get().uuid())) {
            throw new IllegalArgumentException("Caller and player UUIDs differ");
        }

        this.correlationId = correlationId;
        this.capturedAt = capturedAt;
        this.caller = caller;
        this.player = player;
        this.registries = registries;
        this.recipes = recipes;
        this.observableGameState = observableGameState;
        this.metrics = metrics;
        this.unrestrictedJavascript = unrestrictedJavascript;
    }
    public String correlationId() { return correlationId; }
    public Instant capturedAt() { return capturedAt; }
    public CallerSnapshot caller() { return caller; }
    public Optional<PlayerSnapshot> player() { return player; }
    public Optional<RegistrySnapshot> registries() { return registries; }
    public Optional<RecipeSnapshot> recipes() { return recipes; }
    public Optional<ObservableGameStateSnapshot> observableGameState() { return observableGameState; }
    public ContextMetrics metrics() { return metrics; }
    public boolean unrestrictedJavascript() { return unrestrictedJavascript; }
public ToolInvocationContext(
            String correlationId, Instant capturedAt, CallerSnapshot caller,
            Optional<PlayerSnapshot> player, Optional<RegistrySnapshot> registries,
            Optional<RecipeSnapshot> recipes, Optional<ObservableGameStateSnapshot> observableGameState,
            ContextMetrics metrics) {
        this(correlationId, capturedAt, caller, player, registries, recipes, observableGameState, metrics, false);
    }
public ToolInvocationContext(
            String correlationId,
            Instant capturedAt,
            CallerSnapshot caller,
            Optional<PlayerSnapshot> player,
            Optional<RegistrySnapshot> registries,
            Optional<RecipeSnapshot> recipes,
            ContextMetrics metrics) {
        this(correlationId, capturedAt, caller, player, registries, recipes, Optional.empty(), metrics, false);
    }
public static ToolInvocationContext developmentConsole(String correlationId) {
        return new ToolInvocationContext(
                correlationId,
                Instant.now(),
                new CallerSnapshot(CallerKind.CONSOLE, null, "Development Console", true),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new ContextMetrics(0, 0, 0, 0, 0));
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolInvocationContext)) return false;
        ToolInvocationContext that = (ToolInvocationContext) other;
        return java.util.Objects.equals(correlationId, that.correlationId) && java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(caller, that.caller) && java.util.Objects.equals(player, that.player) && java.util.Objects.equals(registries, that.registries) && java.util.Objects.equals(recipes, that.recipes) && java.util.Objects.equals(observableGameState, that.observableGameState) && java.util.Objects.equals(metrics, that.metrics) && unrestrictedJavascript == that.unrestrictedJavascript;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(correlationId);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(caller);
        hash = 31 * hash + java.util.Objects.hashCode(player);
        hash = 31 * hash + java.util.Objects.hashCode(registries);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        hash = 31 * hash + java.util.Objects.hashCode(observableGameState);
        hash = 31 * hash + java.util.Objects.hashCode(metrics);
        hash = 31 * hash + Boolean.hashCode(unrestrictedJavascript);
        return hash;
    }
    @Override public String toString() { return "ToolInvocationContext[correlationId=" + correlationId + ", capturedAt=" + capturedAt + ", caller=" + caller + ", player=" + player + ", registries=" + registries + ", recipes=" + recipes + ", observableGameState=" + observableGameState + ", metrics=" + metrics + ", unrestrictedJavascript=" + unrestrictedJavascript + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolInvocationContext> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolInvocationContext.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolInvocationContext>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "correlationId", ToolInvocationContext::correlationId), new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "capturedAt", ToolInvocationContext::capturedAt), new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "caller", ToolInvocationContext::caller), new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "player", ToolInvocationContext::player), new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "registries", ToolInvocationContext::registries), new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "recipes", ToolInvocationContext::recipes), new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "observableGameState", ToolInvocationContext::observableGameState), new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "metrics", ToolInvocationContext::metrics), new dev.openallay.value.ValueSchema.Component<>(ToolInvocationContext.class, "unrestrictedJavascript", ToolInvocationContext::unrestrictedJavascript)), arguments -> new ToolInvocationContext((String) arguments[0], (Instant) arguments[1], (CallerSnapshot) arguments[2], (Optional) arguments[3], (Optional) arguments[4], (Optional) arguments[5], (Optional) arguments[6], (ContextMetrics) arguments[7], (Boolean) arguments[8]));
        }
    }
}
