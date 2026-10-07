package dev.openallay.script.schema;

import com.google.gson.JsonElement;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.openallay.script.host.HostAccessException;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates declared schemas from the same closed Java type algebra used by
 * {@link dev.openallay.script.host.RhinoHostAdapter}.
 */
public final class RhinoTypeSchema {
    private RhinoTypeSchema() {}

    public static HostSchema require(Type type) {
        return require(TypeInfo.of(java.util.Objects.requireNonNull(type, "type")));
    }

    public static HostSchema require(TypeInfo type) {
        java.util.Objects.requireNonNull(type, "type");
        Class<?> raw = type.asClass();
        if (raw == null || raw == Object.class) {
            throw unsupported(type);
        }
        if (raw == String.class || raw == Character.class || raw == char.class
                || raw == UUID.class || TemporalAccessor.class.isAssignableFrom(raw)
                || TemporalAmount.class.isAssignableFrom(raw)) {
            return new HostSchema.Scalar("string");
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return new HostSchema.Scalar("boolean");
        }
        if (Number.class.isAssignableFrom(raw)
                || raw == byte.class || raw == short.class || raw == int.class
                || raw == long.class || raw == float.class || raw == double.class) {
            return new HostSchema.Scalar("number");
        }
        if (raw.isEnum()) {
            List<String> values = type.enumConstants().stream()
                    .map(value -> ((Enum<?>) value).name())
                    .toList();
            return new HostSchema.Enumeration("enum", values);
        }
        if (JsonElement.class.isAssignableFrom(raw)) {
            return new HostSchema.DynamicJson("json");
        }
        if (raw == HostSchema.class) {
            return new HostSchema.DynamicDetached("schema");
        }
        if (Optional.class.isAssignableFrom(raw)) {
            return new HostSchema.OptionalValue("optional", requireParameter(type, 0));
        }
        if (Collection.class.isAssignableFrom(raw)) {
            return new HostSchema.Sequence("list", requireParameter(type, 0));
        }
        if (Map.class.isAssignableFrom(raw)) {
            TypeInfo key = type.param(0);
            if (key.asClass() != String.class) {
                throw new HostAccessException(
                        "javascript_host_type_unsupported",
                        "Only String-keyed detached maps are available to JavaScript");
            }
            return new HostSchema.Dictionary("map", requireParameter(type, 1), true);
        }
        if (ValueSchemas.supports(raw)) {
            LinkedHashMap<String, HostSchema> fields = new LinkedHashMap<>();
            for (ValueSchema.Component<?> component : explicitSchema(raw).components()) {
                fields.put(component.name(), require(TypeInfo.of(component.genericType())));
            }
            return new HostSchema.RecordValue("record", raw.getName(), fields);
        }
        if (raw.isRecord()) {
            LinkedHashMap<String, HostSchema> fields = new LinkedHashMap<>();
            for (RecordComponent component : raw.getRecordComponents()) {
                fields.put(component.getName(), require(TypeInfo.of(component.getGenericType())));
            }
            return new HostSchema.RecordValue("record", raw.getName(), fields);
        }
        throw unsupported(type);
    }

    public static void validateValue(Type declaredType, Object value) {
        validateValue(TypeInfo.of(declaredType), require(declaredType), value);
    }

    private static void validateValue(TypeInfo declared, HostSchema schema, Object value) {
        if (value == null) {
            return;
        }
        if (schema instanceof HostSchema.OptionalValue optional) {
            if (!(value instanceof Optional<?> actual)) {
                throw mismatch(declared, value);
            }
            actual.ifPresent(item -> validateValue(declared.param(0), optional.value(), item));
            return;
        }
        if (schema instanceof HostSchema.Sequence sequence) {
            if (!(value instanceof Collection<?> actual)) {
                throw mismatch(declared, value);
            }
            TypeInfo elementType = declared.param(0);
            for (Object item : actual) {
                validateValue(elementType, sequence.elements(), item);
            }
            return;
        }
        if (schema instanceof HostSchema.Dictionary dictionary) {
            if (!(value instanceof Map<?, ?> actual)) {
                throw mismatch(declared, value);
            }
            TypeInfo valueType = declared.param(1);
            for (Map.Entry<?, ?> entry : actual.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new HostAccessException(
                            "javascript_host_map_key_unsupported",
                            "Only String-keyed detached maps are available to JavaScript");
                }
                validateValue(valueType, dictionary.values(), entry.getValue());
            }
            return;
        }
        if (schema instanceof HostSchema.RecordValue record) {
            if (!declared.asClass().isInstance(value)) {
                throw mismatch(declared, value);
            }
            if (ValueSchemas.supports(declared.asClass())) {
                validateExplicitValue(declared.asClass(), record, value);
                return;
            }
            RecordComponent[] components = declared.asClass().getRecordComponents();
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        declared.asClass(), MethodHandles.lookup());
                for (RecordComponent component : components) {
                    Object child = lookup.unreflect(component.getAccessor()).invoke(value);
                    TypeInfo childType = TypeInfo.of(component.getGenericType());
                    validateValue(childType, record.fields().get(component.getName()), child);
                }
            } catch (Throwable failure) {
                throw new HostAccessException(
                        "javascript_host_access_failed",
                        "Could not validate detached record components");
            }
            return;
        }
        if (schema instanceof HostSchema.DynamicJson) {
            if (!(value instanceof JsonElement)) {
                throw mismatch(declared, value);
            }
            return;
        }
        Class<?> raw = declared.asClass();
        if (!boxed(raw).isInstance(value)) {
            throw mismatch(declared, value);
        }
    }

    private static <T> ValueSchema<T> explicitSchema(Class<T> owner) {
        try {
            return ValueSchemas.of(owner);
        } catch (RuntimeException failure) {
            throw unsupported(TypeInfo.of(owner));
        }
    }

    private static <T> void validateExplicitValue(
            Class<T> owner, HostSchema.RecordValue record, Object value) {
        ValueSchema<T> schema = explicitSchema(owner);
        T actual = owner.cast(value);
        for (ValueSchema.Component<T> component : schema.components()) {
            Object child;
            try {
                child = component.read(actual);
            } catch (Throwable failure) {
                throw new HostAccessException(
                        "javascript_host_access_failed",
                        "Could not validate detached value components");
            }
            validateValue(TypeInfo.of(component.genericType()),
                    record.fields().get(component.name()), child);
        }
    }

    private static HostSchema requireParameter(TypeInfo type, int index) {
        TypeInfo parameter = type.param(index);
        if (parameter == null
                || parameter == TypeInfo.NONE
                || parameter.asClass() == null
                || parameter.asClass() == Object.class) {
            throw unsupported(type);
        }
        return require(parameter);
    }

    private static HostAccessException unsupported(TypeInfo type) {
        return new HostAccessException(
                "javascript_host_type_unsupported",
                "Detached Java type is not available to JavaScript: " + type.signature());
    }

    private static HostAccessException mismatch(TypeInfo type, Object value) {
        return new HostAccessException(
                "javascript_host_type_mismatch",
                "Detached value does not match declared JavaScript type "
                        + type.signature() + ": " + value.getClass().getName());
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
