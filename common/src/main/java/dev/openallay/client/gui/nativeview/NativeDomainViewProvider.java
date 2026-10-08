package dev.openallay.client.gui.nativeview;

/** Optional provider for one closed native domain-view family. */
public interface NativeDomainViewProvider {
    String providerId();

    int priority();

    boolean supports(NativeDomainViewBinding binding);

    Attempt create(NativeDomainViewBinding binding);

    interface Attempt {
        /** Exact published variant admission at the real aggregate/provider boundary. Null is unchanged. */
        static Attempt requireKnown(Attempt value) {
            if (value == null) return null;
            Class<?> actual = value.getClass();
            if (actual == Ready.class || actual == Unsupported.class) return value;
            throw new IncompatibleClassChangeError("Unknown native Attempt subtype");
        }

        @dev.openallay.value.ValueType(Ready.ValueSchemaProvider.class)
public static final class Ready implements Attempt {
    private final NativeDomainView view;
    public Ready(NativeDomainView view) {

                java.util.Objects.requireNonNull(view, "view");

        this.view = view;
    }
    public NativeDomainView view() { return view; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Ready)) return false;
        Ready that = (Ready) other;
        return java.util.Objects.equals(view, that.view);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(view);
        return hash;
    }
    @Override public String toString() { return "Ready[view=" + view + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Ready> schema() {
            return new dev.openallay.value.ValueSchema<>(Ready.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Ready>>asList(new dev.openallay.value.ValueSchema.Component<>(Ready.class, "view", Ready::view)), arguments -> new Ready((NativeDomainView) arguments[0]));
        }
    }
}

        @dev.openallay.value.ValueType(Unsupported.ValueSchemaProvider.class)
public static final class Unsupported implements Attempt {
    private final String code;
    public Unsupported(String code) {

                if (code == null || !code.matches("[a-z0-9_]+")) {
                    throw new IllegalArgumentException("native view diagnostic code is invalid");
                }

        this.code = code;
    }
    public String code() { return code; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Unsupported)) return false;
        Unsupported that = (Unsupported) other;
        return java.util.Objects.equals(code, that.code);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        return hash;
    }
    @Override public String toString() { return "Unsupported[code=" + code + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Unsupported> schema() {
            return new dev.openallay.value.ValueSchema<>(Unsupported.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Unsupported>>asList(new dev.openallay.value.ValueSchema.Component<>(Unsupported.class, "code", Unsupported::code)), arguments -> new Unsupported((String) arguments[0]));
        }
    }
}
    }
}
