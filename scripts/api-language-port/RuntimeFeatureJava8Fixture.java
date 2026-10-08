package dev.openallay.util;
public final class RuntimeFeatureJava8Fixture {
    public static void main(String[] args) throws Exception {
        int actual = Java8ApiSupport.runtimeVersionFeature();
        if ("1.8".equals(System.getProperty("java.specification.version"))) {
            if (actual != 8) throw new AssertionError("Java8 feature fact");
        } else {
            Object version = Runtime.class.getMethod("version").invoke(null);
            int expected = (Integer) version.getClass().getMethod("feature").invoke(version);
            if (actual != expected) throw new AssertionError("Public modern feature fact");
        }
        String original = System.getProperty("java.specification.version");
        try {
            System.setProperty("java.specification.version", "999");
            if (Java8ApiSupport.runtimeVersionFeature() != actual)
                throw new AssertionError("Mutable property changed running JVM fact");
        } finally {
            if (original == null) System.clearProperty("java.specification.version");
            else System.setProperty("java.specification.version", original);
        }
        System.out.println("PASS external current JVM feature fact=" + actual);
    }
}
