package dev.openallay.value;

/** One canonical explicit schema registry. Providers use public constructors only. */
public final class ValueSchemas {
    private ValueSchemas() {}
    private static final ClassValue<ValueSchema<?>> CACHE = new ClassValue<ValueSchema<?>>() {
        @Override protected ValueSchema<?> computeValue(Class<?> owner) {
            ValueType declaration = owner.getAnnotation(ValueType.class);
            if (declaration == null) throw new IllegalArgumentException("Not an explicit value: " + owner.getName());
            try {
                ValueSchema<?> schema = declaration.value().getConstructor().newInstance().schema();
                if (schema == null || schema.owner() != owner) throw new IllegalArgumentException("Value schema owner differs");
                return schema;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Value schema requires a public provider: " + owner.getName(), failure);
            }
        }
    };
    /** Public optional modern-JVM fact; no Java9+ symbol appears in this Java8 class. */
    public static boolean isValue(Class<?> owner) {
        if (supports(owner)) return true;
        try { return Boolean.TRUE.equals(Class.class.getMethod("isRecord").invoke(owner)); }
        catch (NoSuchMethodException absentOnJava8) { return false; }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect public record fact", failure); }
    }
    public static boolean supports(Class<?> owner) { return owner.getAnnotation(ValueType.class) != null; }
    @SuppressWarnings("unchecked")
    public static <T> ValueSchema<T> of(Class<T> owner) { return (ValueSchema<T>) CACHE.get(owner); }
}
