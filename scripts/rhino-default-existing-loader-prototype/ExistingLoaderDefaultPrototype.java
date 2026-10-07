package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.classfile.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;

/** Genuine canonical generator and its preexisting loader; no new loader implementation. */
public final class ExistingLoaderDefaultPrototype {
    public interface Parent {
        String callback(String text);
        default String text() { return "default"; }
        default Object self() { return this; }
        default String call() { return callback("callback"); }
        default int add(int a, int b) { return a + b; }
        default void failure() { throw new IllegalStateException("default-failure"); }
    }
    public interface Child extends Parent { default String text() { return "child"; } }
    public interface Left extends Parent {}
    public interface Right extends Parent {}
    public interface Diamond extends Left, Right {}
    interface Hidden { default String text() { return "hidden"; } }
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check=" + checks); }
    static Class<?> generate(Context cx, Class<?> root, String name) throws Exception {
        if (!root.isInterface() || !Modifier.isPublic(root.getModifiers())) throw new IllegalArgumentException("Prototype requires a public interface");
        for (Class<?> outer = root; outer != null; outer = outer.getEnclosingClass()) {
            if (!Modifier.isPublic(outer.getModifiers())) throw new IllegalArgumentException("Nonpublic enclosing owner");
        }
        ClassFileWriter writer = new ClassFileWriter(name, "java/lang/Object", "default-prototype", 52, 0);
        writer.setFlags((short) (ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_INTERFACE | ClassFileWriter.ACC_ABSTRACT));
        writer.addInterface(root.getName());
        writer.startMethod("ownerLookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", (short) (ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC));
        writer.addInvoke(ByteCode.INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
        writer.add(ByteCode.ARETURN); writer.stopMethod((short) 0);
        byte[] bytes = writer.toByteArray();
        check(((bytes[6] & 255) << 8 | (bytes[7] & 255)) == 52);
        // Existing canonical method owns createClassLoader/define/link; no custom loader added.
        Class<?> generated = JavaAdapter.loadAdapterClass(cx, name, bytes);
        check(generated.isInterface()); check(root.isAssignableFrom(generated));
        return generated;
    }
    static void vector(Class<?> root, String name) throws Throwable {
        Context cx = new ContextFactory().enter(); cx.setApplicationClassLoader(root.getClassLoader());
        Class<?> generated = generate(cx, root, name);
        MethodHandles.Lookup lookup = (MethodHandles.Lookup) generated.getMethod("ownerLookup").invoke(null);
        int[] callbacks = {0}; Object target = new Object();
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("equals")) return proxy == arguments[0];
                if (method.getName().equals("hashCode")) return target.hashCode();
                if (method.getName().equals("toString")) return "Proxy[" + target + "]";
            }
            if (!method.isDefault()) { callbacks[0]++; return "js:" + arguments[0]; }
            MethodHandle handle = lookup.findSpecial(root, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()), generated);
            return handle.bindTo(proxy).invokeWithArguments(arguments == null ? new Object[0] : arguments);
        };
        Parent proxy = (Parent) Proxy.newProxyInstance(generated.getClassLoader(), new Class<?>[]{root, generated}, handler);
        check(Proxy.isProxyClass(proxy.getClass())); check(Proxy.getInvocationHandler(proxy) == handler);
        check(proxy.self() == proxy); check(proxy.call().equals("js:callback")); check(callbacks[0] == 1);
        check(proxy.add(2, 3) == 5); check(proxy.text().equals(root == Child.class ? "child" : "default"));
        try { proxy.failure(); throw new AssertionError("No default exception"); }
        catch (IllegalStateException expected) { check(expected.getMessage().equals("default-failure")); }
        check(proxy.equals(proxy)); check(!proxy.equals(new Object())); check(proxy.hashCode() == target.hashCode());
        check(proxy.toString().equals("Proxy[" + target + "]"));
        check(proxy.getClass().getInterfaces().length == 2); // explicit observable extra interface
    }
    public static void main(String[] args) throws Throwable {
        if (!"1.8".equals(System.getProperty("java.specification.version"))) throw new AssertionError("Require genuine Java8 execution");
        vector(Parent.class, "DefaultPrototypeParent");
        vector(Child.class, "DefaultPrototypeChild");
        vector(Diamond.class, "DefaultPrototypeDiamond");
        Context cx = new ContextFactory().enter();
        try { generate(cx, Hidden.class, "DefaultPrototypeHidden"); throw new AssertionError("Nonpublic interface accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("PASS genuineJava8 existing-loader default prototype checks=" + checks + " runtime=" + System.getProperty("java.version"));
    }
}
