package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class NativeJavaRuntimeTest {
    @Test void nativeOptionTracksActualSpecificationVersion() {
        for (String version : new String[] {"1.8", "8", "9", "11", "16"}) assertFalse(NativeJavaRuntime.supportsNativeAccess(version));
        for (String version : new String[] {"17", "21", "25", "26.1"}) assertTrue(NativeJavaRuntime.supportsNativeAccess(version));
        assertThrows(IllegalStateException.class, () -> NativeJavaRuntime.supportsNativeAccess(null));
        assertThrows(NumberFormatException.class, () -> NativeJavaRuntime.supportsNativeAccess("unknown"));
    }
    @Test void actualPlatformParentUsesPublicRuntimeCapability() throws Exception {
        ClassLoader expected;
        try { expected = (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null); }
        catch (NoSuchMethodException java8) { expected = ClassLoader.getSystemClassLoader().getParent(); }
        assertSame(expected, NativeJavaRuntime.platformParent());
        assertNotSame(ClassLoader.getSystemClassLoader(), NativeJavaRuntime.platformParent());
        assertEquals("java.lang.String", NativeJavaRuntime.platformParent().loadClass("java.lang.String").getName());
    }
    @Test void discardUsesOperatingSystemSinkWithoutGameOrModelFiles() {
        String expected = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows") ? "NUL" : "/dev/null";
        assertEquals(expected, NativeJavaRuntime.discardFile().getPath());
    }
}
