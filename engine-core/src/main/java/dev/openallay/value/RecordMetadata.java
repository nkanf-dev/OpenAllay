package dev.openallay.value;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Optional public JVM record metadata. Explicit immutable values use ValueSchemas instead. */
public final class RecordMetadata {
    private RecordMetadata() {}
    private static final Method IS_RECORD = optionalClassMethod("isRecord");
    private static final Method RECORD_COMPONENTS = optionalClassMethod("getRecordComponents");

    private static Method optionalClassMethod(String name) {
        try { return Class.class.getMethod(name); }
        catch (NoSuchMethodException absentOnJava8) { return null; }
    }

    public static boolean isRecord(Class<?> owner) {
        Objects.requireNonNull(owner, "owner");
        return IS_RECORD != null && Boolean.TRUE.equals(invoke(IS_RECORD, owner));
    }

    /** Ordered metadata only. No constructor calls or private field reads are authorized here. */
    public static List<Component> components(Class<?> owner) {
        Objects.requireNonNull(owner, "owner");
        if (!isRecord(owner) || RECORD_COMPONENTS == null) {
            throw new IllegalArgumentException("Not a record: " + owner.getName());
        }
        Object[] records = (Object[]) invoke(RECORD_COMPONENTS, owner);
        if (records == null) throw new IllegalStateException("Record components are absent: " + owner.getName());
        ArrayList<Component> result = new ArrayList<>();
        for (Object record : records) {
            String name = (String) fact(record, "getName");
            try {
                result.add(new Component(name, (Type) fact(record, "getGenericType"),
                        (Class<?>) fact(record, "getType"), (Method) fact(record, "getAccessor"),
                        owner.getDeclaredField(name), (AnnotatedElement) record));
            } catch (NoSuchFieldException failure) {
                throw new IllegalStateException("Missing record component field: " + name, failure);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static Object fact(Object record, String name) {
        try { return invoke(record.getClass().getMethod(name), record); }
        catch (NoSuchMethodException failure) {
            throw new IllegalStateException("Missing public record metadata: " + name, failure);
        }
    }

    private static Object invoke(Method method, Object target) {
        try { return method.invoke(target); }
        catch (InvocationTargetException failure) {
            throw new IllegalStateException("Cannot read public record metadata: " + method.getName(), failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read public record metadata: " + method.getName(), failure);
        }
    }

    public static final class Component {
        private final String name;
        private final Type genericType;
        private final Class<?> rawType;
        private final Method accessor;
        private final Field field;
        private final AnnotatedElement record;
        private Component(String name, Type genericType, Class<?> rawType, Method accessor,
                Field field, AnnotatedElement record) {
            this.name = name; this.genericType = genericType; this.rawType = rawType;
            this.accessor = accessor; this.field = field; this.record = record;
        }
        public String name() { return name; }
        public Type genericType() { return genericType; }
        public Class<?> rawType() { return rawType; }
        public Method accessorMetadata() { return accessor; }
        public Field fieldMetadata() { return field; }
        public <A extends Annotation> A annotation(Class<A> type) {
            A found = record.getAnnotation(type);
            if (found == null) found = field.getAnnotation(type);
            return found == null ? accessor.getAnnotation(type) : found;
        }
    }
}
