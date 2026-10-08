package dev.openallay.client.voice;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

/** Uses the current JVM's own executable, native-access option and platform-parent capabilities. */
final class NativeJavaRuntime {
    private NativeJavaRuntime() {}
    static boolean supportsNativeAccess() {
        return supportsNativeAccess(System.getProperty("java.specification.version"));
    }
    static boolean supportsNativeAccess(String version) {
        if (version == null) throw new IllegalStateException("Missing java.specification.version");
        String major = version.startsWith("1.") ? version.substring(2) : version;
        int dot = major.indexOf('.');
        if (dot >= 0) major = major.substring(0, dot);
        return Integer.parseInt(major) >= 17;
    }
    static File discardFile() {
        return new File(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .startsWith("windows") ? "NUL" : "/dev/null");
    }
    static ClassLoader platformParent() {
        try {
            return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (NoSuchMethodException java8) {
            ClassLoader system = ClassLoader.getSystemClassLoader();
            return system == null ? null : system.getParent();
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException("Cannot access public platform class loader", impossible);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Cannot obtain platform class loader", cause);
        }
    }
}
