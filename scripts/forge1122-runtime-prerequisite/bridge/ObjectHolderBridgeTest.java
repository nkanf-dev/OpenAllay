package dev.openallay.runtime.forge1122;
import java.util.jar.JarFile;
import java.io.InputStream;
public final class ObjectHolderBridgeTest {
    public static void main(String[] args) throws Exception {
        try(JarFile jar=new JarFile(args[0])) {
            for(String name:new String[]{"ObjectHolderRef$FinalFieldHelper","ObjectHolderRegistry"}) {
                byte[] bytes;
                try(InputStream input=jar.getInputStream(jar.getJarEntry("net/minecraftforge/registries/"+name+".class"))) {
                    java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
                    while((n=input.read(buffer))!=-1)output.write(buffer,0,n);bytes=output.toByteArray();
                }
                byte[] patched=name.endsWith("Registry")?ObjectHolderBridge.patchRegistry(bytes):ObjectHolderBridge.patch(bytes);
                java.util.Map<String,String> before=ObjectHolderBridge.methodInstructions(bytes),after=ObjectHolderBridge.methodInstructions(patched);
                if(name.endsWith("Registry")) { before.remove("scanClassForFields(Ljava/util/Map;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Class;Z)V"); after.remove("scanClassForFields(Ljava/util/Map;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Class;Z)V"); }
                else { before.remove("makeWritable(Ljava/lang/reflect/Field;)Ljava/lang/reflect/Field;");after.remove("makeWritable(Ljava/lang/reflect/Field;)Ljava/lang/reflect/Field;");before.remove("setField(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V");after.remove("setField(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V"); }
                if(!before.equals(after))throw new AssertionError("Unrelated instructions changed");
                bytes[bytes.length-1]^=1;
                try { if(name.endsWith("Registry"))ObjectHolderBridge.patchRegistry(bytes);else ObjectHolderBridge.patch(bytes);throw new AssertionError("Wrong bytes accepted"); }
                catch(IllegalStateException expected){}
            }
        }
        System.out.println("PASS exact Forge ObjectHolder helper/scan seams; unrelated discovery/apply unchanged; altered bytes reject");
    }
}
