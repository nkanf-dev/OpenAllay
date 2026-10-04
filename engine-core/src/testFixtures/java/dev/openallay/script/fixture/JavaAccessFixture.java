package dev.openallay.script.fixture;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Synthetic host values. No game, files, environment, or provider state is involved. */
public final class JavaAccessFixture extends JavaAccessParent implements JavaAccessContract {
    public static final String STATIC_FINAL = "constant";
    public static String publicStatic = "public-static";
    private static String privateStatic = "private-static";
    private String hidden = "child";
    private String privateValue = "player_value";
    protected int protectedValue = 2;
    int packageValue = 3;
    public final int finalValue = 7;
    private String nullable;
    private int[] numbers = {2, 4};
    private String label;

    private JavaAccessFixture() { label = "empty"; }
    private JavaAccessFixture(int value) { label = "int:" + value; }
    private JavaAccessFixture(String value) { label = "string:" + value; }
    private JavaAccessFixture(String... values) { label = "strings:" + String.join("|", values); }

    public static JavaAccessFixture create() { return new JavaAccessFixture(); }
    public static Class<?> nativeClass() { return JavaAccessFixture.class; }
    public static Class<?> inspectionOnlyClass() throws ClassNotFoundException {
        return Class.forName(JavaAccessFixture.class.getName() + "$InspectionOnly", false,
                JavaAccessFixture.class.getClassLoader());
    }
    public static Object createIsolated() throws ReflectiveOperationException {
        ClassLoader loader = new FixtureLoader(JavaAccessFixture.class.getClassLoader());
        return Class.forName("dev.openallay.script.fixture.LoaderFixture", true, loader)
                .getDeclaredConstructor().newInstance();
    }
    public static Object createShadowHierarchy() throws ReflectiveOperationException {
        ClassLoader loader = new FixtureLoader(JavaAccessFixture.class.getClassLoader());
        return Class.forName("dev.openallay.script.fixture.ShadowChildFixture", true, loader)
                .getDeclaredConstructor().newInstance();
    }
    public static Object applicationParameter() { return new ShadowParameter(); }
    public static Class<?> failingInitializerClass() throws ClassNotFoundException {
        return Class.forName("dev.openallay.script.fixture.FailingInitializerFixture", false,
                new FixtureLoader(JavaAccessFixture.class.getClassLoader()));
    }
    public static JavaAccessParent asParent() { return new JavaAccessFixture(); }
    public static String publicStaticMethod(String value) { return "public:" + value; }

    private static String staticMethod(int value) { return "static:" + value; }
    private String instanceMethod(int value) { return "instance:" + value; }
    private String overload(int value) { return "int:" + value; }
    private String overload(long value) { return "long:" + value; }
    private String overload(String value) { return "string:" + value; }
    private String overload(Object value) { return "object:" + value; }
    private int sum(int[] values) { return Arrays.stream(values).sum(); }
    private int matrix(int[][] values) {
        int result = 0;
        for (int[] row : values) result += Arrays.stream(row).sum();
        return result;
    }
    private String join(String prefix, String... values) { return prefix + String.join("|", values); }
    private boolean invert(boolean value) { return !value; }
    private char next(char value) { return (char) (value + 1); }
    private double half(double value) { return value / 2; }
    private Object identity(Object value) { return value; }
    private void update(String value) { privateValue = value; }
    private void explode() { throw new IllegalStateException("fixture method failed: player_value"); }
    private void cancelTarget() {
        dev.openallay.model.CancellationSignal signal = new dev.openallay.model.CancellationSignal();
        signal.cancel();
        signal.throwIfCancelled();
    }

    private static final class FixtureLoader extends ClassLoader {
        private static final java.util.Set<String> TARGETS = java.util.Set.of(
                "dev.openallay.script.fixture.LoaderFixture",
                "dev.openallay.script.fixture.ShadowChildFixture",
                "dev.openallay.script.fixture.ShadowParameter",
                "dev.openallay.script.fixture.FailingInitializerFixture");
        private FixtureLoader(ClassLoader parent) { super(parent); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!TARGETS.contains(name)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    String resource = name.replace('.', '/') + ".class";
                    try (var input = getParent().getResourceAsStream(resource)) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }

    public static final class InitializationProbe {
        public static int count;
    }

    public static final class InspectionOnly {
        static { InitializationProbe.count++; }
        private static String value = "initialized";
        private InspectionOnly() {}
    }

    public record FinalRecord(String value) {}

    public static final class FailingConstructor {
        private FailingConstructor(String value) {
            throw new IllegalArgumentException("fixture constructor failed: " + value);
        }
    }

    public static final class ThrowingValue {
        @Override public String toString() { throw new AssertionError("Metadata must not read field values"); }
    }

    public static class GenericValue<T> {
        public T item() { return null; }
    }

    public static final class StringValue extends GenericValue<String> {
        @Override public String item() { return "bridge-value"; }
    }

    public static final class MetadataOnly {
        private final ThrowingValue value = new ThrowingValue();
        public static MetadataOnly create() { return new MetadataOnly(); }
    }

    public static final class Blocking {
        public static CountDownLatch entered = new CountDownLatch(1);
        public static CountDownLatch release = new CountDownLatch(1);
        private static String waitForRelease() throws InterruptedException {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture wait timed out");
            return "released";
        }
    }
}
