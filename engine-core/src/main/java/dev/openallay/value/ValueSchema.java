package dev.openallay.value;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Ordered metadata and owner-written calls for one immutable value type. */
public final class ValueSchema<T> {
    public interface Provider { ValueSchema<?> schema(); }
    @FunctionalInterface public interface Accessor<T> { Object read(T value); }
    @FunctionalInterface public interface Constructor<T> { T construct(Object[] arguments); }

    public static final class Component<T> {
        private final String name;
        private final Field field;
        private final Type genericType;
        private final Class<?> rawType;
        private final Accessor<T> accessor;
        public Component(Class<T> owner, String name, Accessor<T> accessor) {
            this(name, field(owner, name), method(owner, name).getGenericReturnType(),
                    method(owner, name).getReturnType(), accessor);
        }
        public Component(String name, Field field, Type genericType, Class<?> rawType, Accessor<T> accessor) {
            this.name = Objects.requireNonNull(name, "name");
            this.field = Objects.requireNonNull(field, "field");
            this.genericType = Objects.requireNonNull(genericType, "genericType");
            this.rawType = Objects.requireNonNull(rawType, "rawType");
            this.accessor = Objects.requireNonNull(accessor, "accessor");
            if (!name.equals(field.getName()) || rawType != field.getType()) {
                throw new IllegalArgumentException("Value accessor type differs: " + name);
            }
        }
        private static Field field(Class<?> owner, String name) {
            try { return owner.getDeclaredField(name); }
            catch (NoSuchFieldException failure) { throw new IllegalArgumentException("Missing declared value component: " + name, failure); }
        }
        private static Method method(Class<?> owner, String name) {
            try { return owner.getMethod(name); }
            catch (NoSuchMethodException failure) { throw new IllegalArgumentException("Missing public value accessor: " + name, failure); }
        }
        public String name() { return name; }
        public Type genericType() { return genericType; }
        public Class<?> rawType() { return rawType; }
        public Field fieldMetadata() { return field; }
        public <A extends Annotation> A annotation(Class<A> type) {
            return field.getAnnotation(type);
        }
        public Object read(T value) { return accessor.read(value); }
    }

    private final Class<T> owner;
    private final List<Component<T>> components;
    private final Constructor<T> constructor;
    public ValueSchema(Class<T> owner, List<Component<T>> components, Constructor<T> constructor) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.constructor = Objects.requireNonNull(constructor, "constructor");
        ArrayList<Component<T>> copy = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Component<T> component : components) {
            Objects.requireNonNull(component, "component");
            if (component.fieldMetadata().getDeclaringClass() != owner || !names.add(component.name())) {
                throw new IllegalArgumentException("Duplicate or foreign value component: " + component.name());
            }
            copy.add(component);
        }
        this.components = Collections.unmodifiableList(copy);
    }
    public Class<T> owner() { return owner; }
    public List<Component<T>> components() { return components; }
    public T construct(Object[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != components.size()) throw new IllegalArgumentException("Value argument count differs");
        return owner.cast(constructor.construct(arguments.clone()));
    }
}
