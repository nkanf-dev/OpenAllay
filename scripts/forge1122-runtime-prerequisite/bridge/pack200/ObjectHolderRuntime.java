package dev.openallay.runtime.forge1122.pack200;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Genuine Field.set over exact admitted ObjectHolder references made nonfinal before definition. */
public final class ObjectHolderRuntime {
    private static boolean admitted(Field field) throws Exception {
        String identity = field.getDeclaringClass().getName() + "\t" + field.getName() + "\t" + field.getType().getName();
        List<String> fields = Files.readAllLines(Paths.get(System.getProperty("openallay.objectholder.fields")), StandardCharsets.UTF_8);
        return fields.contains(identity);
    }
    public static boolean isFinal(int modifiers, Field field) throws Exception {
        return Modifier.isFinal(modifiers) || admitted(field);
    }
    public static Field makeWritable(Field field) throws ReflectiveOperationException {
        try {
            if (!admitted(field) || !Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())
                    || field.getType().isPrimitive()) throw new IllegalArgumentException("Foreign or invalid holder field");
            java.net.URL location = field.getDeclaringClass().getProtectionDomain().getCodeSource().getLocation();
            if ("jar".equals(location.getProtocol())) location = ((java.net.JarURLConnection) location.openConnection()).getJarFileURL();
            File source = new File(location.toURI()).getCanonicalFile();
            File client = new File(System.getProperty("openallay.objectholder.client")).getCanonicalFile();
            if (!source.equals(client)) throw new IllegalArgumentException("Holder source differs from official client");
            Class<?> registry = Class.forName("net.minecraftforge.registries.IForgeRegistryEntry", false, field.getDeclaringClass().getClassLoader());
            if (!registry.isAssignableFrom(field.getType())) throw new IllegalArgumentException("Holder must be genuine registry reference");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException(error); }
    }
    public static void setField(Field field, Object instance, Object value) throws ReflectiveOperationException {
        makeWritable(field);
        if (instance != null || value == null || !field.getType().isInstance(value))
            throw new IllegalArgumentException("Holder instance/value type differs");
        field.set(null, value);
        if (field.get(null) != value) throw new IllegalStateException("Actual registry holder write failed");
        try {
            String receipt = field.getDeclaringClass().getName() + "." + field.getName() + "\t" + value.getClass().getName() + "\treadBackIdentity=true\n";
            Files.write(Paths.get(System.getProperty("openallay.objectholder.writes")), receipt.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("openallay.objectholder.fields", args[0]);
        Field foreign = ObjectHolderRuntime.class.getDeclaredField("FOREIGN");
        if (isFinal(0, foreign)) throw new AssertionError("Nonfinal foreign field cannot become admitted");
        if (!isFinal(Modifier.FINAL, foreign)) throw new AssertionError("Foreign final predicate stays true");
        try { makeWritable(foreign); throw new AssertionError("Foreign field must reject"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS exact foreign-holder rejection and unchanged foreign-final predicate");
    }
    private static Object FOREIGN;
}
