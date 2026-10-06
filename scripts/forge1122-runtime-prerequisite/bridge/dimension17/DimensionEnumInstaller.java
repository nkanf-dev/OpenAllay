package dev.openallay.runtime.forge1122;
import java.lang.instrument.Instrumentation;import java.util.Map;import java.util.Set;import java.util.jar.JarFile;
import java.nio.file.Files;import java.nio.file.Paths;import java.nio.charset.StandardCharsets;
public final class DimensionEnumInstaller {
    public static void install(Instrumentation instrumentation) throws Exception {
        String path=System.getProperty("openallay.dimension.helper");
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(path));
        Class<?> helper=Class.forName("dev.openallay.runtime.forge1122.dimension.DimensionEnumRuntime",false,null);
        if(helper.getClassLoader()!=null)throw new IllegalStateException("Dimension helper must be bootstrap-owned");
        instrumentation.redefineModule(Object.class.getModule(),Set.of(),Map.of(),Map.of("java.lang",Set.of(helper.getModule())),Set.of(),Map.of());
        String receipt="{\"publicBootstrapAppend\":true,\"actualHelperBootstrapOwned\":true,\"helperModule\":\""+helper.getModule().toString()
            +"\",\"javaLangOpenedOnlyToHelperModule\":true,\"gameLoaderUnchanged\":true}\n";
        Files.write(Paths.get(System.getProperty("openallay.dimension.bootstrapReceipt")),receipt.getBytes(StandardCharsets.UTF_8));
    }
}
