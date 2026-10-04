package dev.openallay.context;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonSerializer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** JSON byte accounting for detached native context. The supplied Gson adapters are preserved. */
public final class ContextSnapshotMetricsJson {
    private final Gson gson;

    public ContextSnapshotMetricsJson(Gson source) {
        gson = java.util.Objects.requireNonNull(source, "source").newBuilder()
                .registerTypeHierarchyAdapter(Optional.class, (JsonSerializer<Optional<?>>) (value, type, context) ->
                        value.isPresent() ? context.serialize(value.orElseThrow()) : JsonNull.INSTANCE)
                .create();
    }

    public long bytes(Object value) {
        return value == null ? 0 : gson.toJson(value).getBytes(StandardCharsets.UTF_8).length;
    }
}
