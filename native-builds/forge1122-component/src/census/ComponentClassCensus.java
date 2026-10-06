package dev.openallay.forge1122.component;
import java.util.jar.JarFile;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.io.InputStream;
import dev.openallay.internal.forge1122.asm.ClassReader;
import dev.openallay.internal.forge1122.asm.ClassVisitor;
import dev.openallay.internal.forge1122.asm.AnnotationVisitor;
import dev.openallay.internal.forge1122.asm.Opcodes;

/** Truthful whole-byte archive reader; never fabricates or suppresses a class. */
public final class ComponentClassCensus {
    public static void main(String[] args) throws Exception {
        int classes=0,modern=0,annotations=0;
        for(String path:args)try(JarFile jar=new JarFile(path)) {
            Enumeration<JarEntry> entries=jar.entries();
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement();if(!entry.getName().endsWith(".class"))continue;
                byte[] bytes;
                try(InputStream input=jar.getInputStream(entry)) {
                    java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                    while((n=input.read(buffer))!=-1)output.write(buffer,0,n);bytes=output.toByteArray();
                }
                final StringBuilder facts=new StringBuilder();final int[] count={0};
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                    public void visit(int version,int access,String name,String signature,String superName,String[] interfaces) {
                        facts.append(name).append("\tmajor=").append(version&65535);
                    }
                    public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) {
                        facts.append("\tannotation=").append(descriptor);count[0]++;return null;
                    }
                },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                int major=((bytes[6]&255)<<8)|(bytes[7]&255);
                try { new org.objectweb.asm.ClassReader(bytes); if(major>52)throw new AssertionError("Expected real ASM5 rejection above52"); }
                catch(IllegalArgumentException expected) { if(major<=52)throw new IllegalStateException("Stock ASM5 rejected Java8 class " + entry.getName(), expected); }
                System.out.println(path+"\t"+entry.getName()+"\t"+facts);
                classes++;if(major==61)modern++;annotations+=count[0];
            }
        }
        if(modern==0||classes==0)throw new AssertionError("Actual feature61 inputs required");
        System.err.println("PASS real privateASM9 full classes="+classes+" modern61="+modern+" annotationFacts="+annotations+" stockASM5unchanged=true");
    }
}
