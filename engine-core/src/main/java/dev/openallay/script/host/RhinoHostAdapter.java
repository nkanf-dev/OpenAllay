package dev.openallay.script.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.Undefined;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.script.schema.RhinoTypeSchema;
import dev.openallay.value.ValueSchemas;
import java.lang.reflect.Type;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Closed Java-to-Rhino adapter for immutable detached request snapshots.
 *
 * <p>It never delegates to Rhino's generic Java wrapper. Only explicit value/record components, collection
 * elements, String-keyed map entries, Gson leaves, and stable scalar values are visible.
 */
public final class RhinoHostAdapter {
    private final Context context;
    private final Scriptable scope;
    private final IdentityHashMap<Object, Scriptable> cache = new IdentityHashMap<>();

    public RhinoHostAdapter(Context context, Scriptable scope) {
        this.context = Objects.requireNonNull(context, "context");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    /** Declared-type verdict used by extension registration and schema discovery. */
    public static HostSchema declaredSchema(Type type) {
        return RhinoTypeSchema.require(type);
    }

    public Object adapt(Object value) {
        if (value == null || value == JsonNull.INSTANCE) {
            return null;
        }
        if (value instanceof Optional<?>) {
            Optional<?> optional = (Optional<?>) value;
            return optional.isPresent() ? adapt(optional.get()) : Undefined.INSTANCE;
        }
        if (value instanceof JsonElement) {
            JsonElement json = (JsonElement) value;
            return adaptJson(json);
        }
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character) {
            Character character = (Character) value;
            return character.toString();
        }
        if (value instanceof Enum<?>) {
            Enum<?> enumeration = (Enum<?>) value;
            return enumeration.name();
        }
        if (value instanceof UUID) {
            UUID uuid = (UUID) value;
            return uuid.toString();
        }
        if (value instanceof TemporalAccessor) {
            TemporalAccessor temporal = (TemporalAccessor) value;
            return temporal.toString();
        }
        if (value instanceof TemporalAmount) {
            TemporalAmount temporal = (TemporalAmount) value;
            return temporal.toString();
        }
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            return cached(list, () -> HostListView.list(context, scope, this, list));
        }
        if (value instanceof Set<?>) {
            Set<?> set = (Set<?>) value;
            // JavaScript requires stable numeric indexing. Only the reference vector is copied;
            // every detached element retains its original Java identity.
            return cached(set, () -> HostListView.list(
                    context, scope, this, new ArrayList<>(set)));
        }
        if (value instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) value;
            return cached(collection, () -> HostListView.list(
                    context, scope, this, new ArrayList<>(collection)));
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            return cached(map, () -> HostObjectView.map(context, scope, this, map));
        }
        if (ValueSchemas.supports(value.getClass()) || dev.openallay.value.RecordMetadata.isRecord(value.getClass())) {
            return cached(value, () -> HostObjectView.value(context, scope, this, value));
        }
        throw HostAccessException.unsupported(value.getClass());
    }

    public Object adaptWorkspace(Object value, JavascriptResultShape shape) {
        Objects.requireNonNull(shape, "shape");
        if (!(value instanceof JsonElement) || !shape.trusted()) {
            return adapt(value);
        }
        JsonElement json = (JsonElement) value;
        if (json.isJsonArray()) {
            return cached(json, () -> HostListView.json(
                    context, scope, this, json.getAsJsonArray(), shape));
        }
        if (json.isJsonObject()) {
            return cached(json, () -> HostObjectView.json(
                    context, scope, this, json.getAsJsonObject(), shape));
        }
        return adapt(value);
    }

    private Object adaptJson(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return primitive.getAsNumber();
            return primitive.getAsString();
        }
        if (value.isJsonArray()) {
            return cached(value, () -> HostListView.json(
                    context, scope, this, value.getAsJsonArray()));
        }
        return cached(value, () -> HostObjectView.json(
                context, scope, this, value.getAsJsonObject()));
    }

    private Scriptable cached(Object identity, Supplier<? extends Scriptable> factory) {
        Scriptable existing = cache.get(identity);
        if (existing != null) {
            return existing;
        }
        Scriptable created = factory.get();
        cache.put(identity, created);
        return created;
    }
}
