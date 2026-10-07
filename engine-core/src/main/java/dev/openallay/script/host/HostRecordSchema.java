package dev.openallay.script.host;

import dev.latvian.mods.rhino.type.TypeInfo;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached, component-only view of a detached value. No Java methods are exposed to scripts. */
final class HostRecordSchema {
    private static final ConcurrentHashMap<Class<?>, HostRecordSchema> CACHE =
            new ConcurrentHashMap<>();

    private final List<String> names;
    private final Map<String, Component> components;

    private HostRecordSchema(Class<?> type) {
        LinkedHashMap<String, Component> resolved = new LinkedHashMap<>();
        if (ValueSchemas.supports(type)) {
            addExplicitComponents(type, resolved);
        } else {
            if (!type.isRecord()) {
                throw HostAccessException.unsupported(type);
            }
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
                for (RecordComponent component : type.getRecordComponents()) {
                    MethodHandle accessor = lookup.unreflect(component.getAccessor());
                    resolved.put(
                            component.getName(),
                            new Component(
                                    value -> accessor.invoke(value),
                                    TypeInfo.of(component.getGenericType())));
                }
            } catch (IllegalAccessException failure) {
                throw new HostAccessException(
                        "javascript_host_type_unsupported",
                        "Detached record components are not accessible: " + type.getName());
            }
        }
        names = List.copyOf(resolved.keySet());
        components = Map.copyOf(resolved);
    }

    private static <T> void addExplicitComponents(
            Class<T> type, Map<String, Component> resolved) {
        try {
            ValueSchema<T> schema = ValueSchemas.of(type);
            for (ValueSchema.Component<T> component : schema.components()) {
                resolved.put(component.name(), new Component(
                        value -> component.read(type.cast(value)),
                        TypeInfo.of(component.genericType())));
            }
        } catch (RuntimeException failure) {
            throw HostAccessException.unsupported(type);
        }
    }

    static HostRecordSchema of(Class<?> type) {
        return CACHE.computeIfAbsent(type, HostRecordSchema::new);
    }

    List<String> names() {
        return names;
    }

    Object read(Object record, String name) {
        Component component = components.get(name);
        if (component == null) {
            return Missing.INSTANCE;
        }
        try {
            return component.accessor().read(record);
        } catch (Throwable failure) {
            throw new HostAccessException(
                    "javascript_host_access_failed",
                    "Could not read detached record component " + name);
        }
    }

    TypeInfo type(String name) {
        Component component = components.get(name);
        return component == null ? TypeInfo.NONE : component.type();
    }

    @FunctionalInterface
    private interface Accessor {
        Object read(Object value) throws Throwable;
    }

    private record Component(Accessor accessor, TypeInfo type) {}

    enum Missing {
        INSTANCE
    }
}
