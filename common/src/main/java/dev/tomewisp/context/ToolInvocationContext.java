package dev.tomewisp.context;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ToolInvocationContext(
        String correlationId,
        Instant capturedAt,
        CallerSnapshot caller,
        Optional<PlayerSnapshot> player,
        Optional<RegistrySnapshot> registries,
        Optional<RecipeSnapshot> recipes,
        ContextMetrics metrics) {
    public ToolInvocationContext {
        correlationId = ContextValidation.nonBlank(correlationId, "correlationId");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(caller, "caller");
        player = Objects.requireNonNull(player, "player");
        registries = Objects.requireNonNull(registries, "registries");
        recipes = Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(metrics, "metrics");
        if (caller.kind() == CallerKind.PLAYER && player.isPresent()
                && !caller.uuid().equals(player.orElseThrow().uuid())) {
            throw new IllegalArgumentException("Caller and player UUIDs differ");
        }
    }

    public static ToolInvocationContext developmentConsole(String correlationId) {
        return new ToolInvocationContext(
                correlationId,
                Instant.now(),
                new CallerSnapshot(CallerKind.CONSOLE, null, "Development Console", true),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new ContextMetrics(0, 0, 0, 0, 0));
    }
}
