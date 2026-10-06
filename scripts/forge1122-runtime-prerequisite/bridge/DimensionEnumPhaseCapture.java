package dev.openallay.runtime.forge1122;
import java.lang.instrument.ClassFileTransformer;import java.lang.instrument.Instrumentation;
import java.nio.file.Files;import java.nio.file.Paths;import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;import java.security.MessageDigest;
public final class DimensionEnumPhaseCapture {
    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader,String name,Class<?> redefined,ProtectionDomain domain,byte[] bytes) {
                if(!"net/minecraft/world/DimensionType".equals(name))return null;
                try {
                    if(redefined!=null||loader==null||!"net.minecraft.launchwrapper.LaunchClassLoader".equals(loader.getClass().getName()))throw new IllegalStateException("Genuine dimension loader required");
                    java.net.URL location=domain.getCodeSource().getLocation();
                    if("jar".equals(location.getProtocol()))location=((java.net.JarURLConnection)location.openConnection()).getJarFileURL();
                    if(!new java.io.File(location.toURI()).getCanonicalFile().equals(new java.io.File(System.getProperty("openallay.objectholder.client")).getCanonicalFile()))throw new IllegalStateException("Official client source required");
                    java.nio.file.Path output=Paths.get(System.getProperty("openallay.dimension.capture"));Files.createDirectories(output);
                    Files.write(output.resolve("DimensionType.class"),bytes);
                    StringBuilder digest=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))digest.append(String.format("%02x",value&255));
                    String receipt="{\"classSha256\":\""+digest+"\",\"source\":\""+domain.getCodeSource().getLocation().toExternalForm()+"\",\"stockLaunchClassLoader\":true,\"accepted\":false}\n";
                    Files.write(output.resolve("phase.json"),receipt.getBytes(StandardCharsets.UTF_8));
                }catch(Exception error){error.printStackTrace();}
                return new byte[]{0};
            }
        },false);
    }
}
