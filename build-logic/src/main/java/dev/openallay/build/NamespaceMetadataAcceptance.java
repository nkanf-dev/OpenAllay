package dev.openallay.build;

import javax.lang.model.element.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Focused actual-classpath tooling acceptance, not an ordinary source-only test. */
public final class NamespaceMetadataAcceptance {
    private NamespaceMetadataAcceptance() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 2 && !(args.length == 3 && args[2].equals("forge1122")))
            throw new IllegalArgumentException("classpath-paths.txt acceptance.properties [forge1122]");
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Path classpathPlan = Path.of(args[0]).toAbsolutePath().normalize();
        List<String> classpathRows = Files.isRegularFile(classpathPlan) ? Files.readAllLines(classpathPlan, StandardCharsets.UTF_8) : List.of();
        List<Path> classpath = new ArrayList<>();
        for (String row : classpathRows) {
            if (row.isEmpty()) continue;
            try { classpath.add(Path.of(row)); }
            catch (InvalidPathException malformed) { /* Reject strictly after safe receipt invalidation. */ }
        }
        List<Path> protectedInputs = new ArrayList<>(classpath); protectedInputs.add(classpathPlan);
        Path temporary = output.resolveSibling(output.getFileName() + ".stage");
        MinecraftClassNamespaceProducer.preflightProtectedWrites(List.of(output, temporary), protectedInputs);
        Files.deleteIfExists(output); Files.deleteIfExists(temporary);
        if (!Files.isRegularFile(classpathPlan)) throw new IllegalStateException("Missing classpath plan");
        for (String row : classpathRows) if (row.isEmpty() || !Path.of(row).isAbsolute()) throw new IllegalStateException("Invalid absolute classpath row");
        try (MinecraftClassNamespaceProducer.Metadata metadata = new MinecraftClassNamespaceProducer.Metadata(classpath)) {
            if (args.length == 3) {
                for (String actual : List.of("net.minecraft.client.gui.GuiScreen", "net.minecraft.block.state.IBlockState",
                        "net.minecraft.block.properties.IProperty", "net.minecraft.world.WorldServer",
                        "net.minecraft.nbt.NBTTagCompound", "net.minecraft.util.IThreadListener")) metadata.binaryType(actual);
                TypeElement nested = metadata.binaryType("net.minecraft.client.gui.toasts.IToast$Visibility");
                if (!nested.getQualifiedName().contentEquals("net.minecraft.client.gui.toasts.IToast.Visibility"))
                    throw new IllegalStateException("Exact1122 nested binary/source class identity missing");
                TypeElement listener = metadata.binaryType("net.minecraft.util.IThreadListener");
                if (!metadata.members(listener).stream().anyMatch(e -> e.getSimpleName().contentEquals("addScheduledTask")))
                    throw new IllegalStateException("Exact1122 native owner member metadata missing");
            } else {
            TypeElement screen = metadata.binaryType("net.minecraft.client.gui.screen.Screen");
            if (screen.getKind() != ElementKind.CLASS || !screen.getModifiers().contains(Modifier.ABSTRACT)) throw new IllegalStateException("Actual abstract Screen contract missing");
            boolean inherited = metadata.members(screen).stream().anyMatch(e -> e.getSimpleName().contentEquals("isDragging") && e.getEnclosingElement() instanceof TypeElement owner && !owner.getQualifiedName().contentEquals(screen.getQualifiedName()));
            if (!inherited) throw new IllegalStateException("Screen inherited FocusableGui member inventory missing");
            TypeElement toast = metadata.binaryType("net.minecraft.client.gui.toasts.IToast$Visibility");
            if (!toast.getQualifiedName().contentEquals("net.minecraft.client.gui.toasts.IToast.Visibility")) throw new IllegalStateException("Nested binary/source distinction missing");
            TypeElement stack = metadata.binaryType("com.mojang.blaze3d.matrix.MatrixStack$Entry");
            if (!stack.getQualifiedName().contentEquals("com.mojang.blaze3d.matrix.MatrixStack.Entry")) throw new IllegalStateException("Nested MatrixStack.Entry missing");
            TypeElement gui = metadata.binaryType("net.minecraft.client.gui.FocusableGui");
            if (!metadata.members(gui).stream().anyMatch(e -> e.getSimpleName().contentEquals("isDragging"))) throw new IllegalStateException("Actual hierarchy member metadata missing");
            }
            // This deliberately missing native type must fail. Recovery-as-success is forbidden.
            boolean rejected = false;
            try { metadata.binaryType("net.minecraft.this_type_must_not_exist.NamespaceAcceptanceAbsent"); }
            catch (IllegalStateException expected) { rejected = true; }
            if (!rejected) throw new IllegalStateException("Missing native type accepted");
        }
        Properties receipt = new Properties();
        receipt.setProperty("accepted", "true");
        receipt.setProperty("jdkIdentity", jdkIdentity());
        receipt.setProperty("toolIdentity", toolIdentity());
        receipt.setProperty("classpathSha256", classpathHash(classpath));
        Files.createDirectories(output.getParent());
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { receipt.store(writer, "Focused actual FG mapped-classpath Elements acceptance"); }
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, output); }
        } catch (Exception failure) { Files.deleteIfExists(temporary); Files.deleteIfExists(output); throw failure; }
    }
    public static void verifyReceipt(Path receiptPath, List<Path> classpath) throws Exception {
        Properties receipt = new Properties();
        try (var reader = Files.newBufferedReader(receiptPath, StandardCharsets.UTF_8)) { receipt.load(reader); }
        if (!receipt.stringPropertyNames().equals(Set.of("accepted", "jdkIdentity", "toolIdentity", "classpathSha256")) || !"true".equals(receipt.getProperty("accepted"))
                || !jdkIdentity().equals(receipt.getProperty("jdkIdentity")) || !toolIdentity().equals(receipt.getProperty("toolIdentity")) || !classpathHash(classpath).equals(receipt.getProperty("classpathSha256"))) throw new IllegalStateException("Metadata acceptance receipt must match exact tooling JDK/classpath");
    }
    public static List<Path> jdkIdentityInputs() throws java.io.IOException {
        Path home = Path.of(System.getProperty("java.home")).toRealPath();
        return List.of(home.resolve("release"), home.resolve("bin/java"), home.resolve("lib/modules"));
    }
    private static List<Path> toolPayload(Class<?> tool) throws java.io.IOException {
        final Path location;
        try { location = Path.of(tool.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath(); }
        catch (java.net.URISyntaxException malformed) { throw new java.io.IOException("Invalid tooling code source", malformed); }
        if (Files.isRegularFile(location)) return List.of(location);
        String prefix = tool.getName().replace('.', '/');
        Path packageDirectory = location.resolve(prefix.substring(0, prefix.lastIndexOf('/')));
        String simple = tool.getSimpleName();
        try (var files = Files.list(packageDirectory)) {
            List<Path> payload = files.filter(Files::isRegularFile).filter(file -> {
                String name = file.getFileName().toString();
                return name.equals(simple + ".class") || name.startsWith(simple + "$") && name.endsWith(".class");
            }).sorted().toList();
            if (payload.isEmpty()) throw new java.io.IOException("Missing exact compiled tooling payload: " + prefix);
            return payload;
        }
    }
    public static List<Path> identityInputPaths() throws java.io.IOException {
        LinkedHashSet<Path> inputs = new LinkedHashSet<>(jdkIdentityInputs());
        for (Class<?> tool : List.of(NamespaceMetadataAcceptance.class, MinecraftClassNamespaceProducer.class, MinecraftClassNamespaceProducerMain.class)) inputs.addAll(toolPayload(tool));
        return List.copyOf(inputs);
    }
    public static String jdkIdentity() throws Exception {
        Path home = Path.of(System.getProperty("java.home")).toRealPath();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String property : List.of("java.vendor", "java.vm.name", "java.vm.vendor", "java.vm.version", "java.runtime.version")) {
            digest.update(property.getBytes(StandardCharsets.UTF_8)); digest.update((byte)0);
            digest.update(System.getProperty(property, "").getBytes(StandardCharsets.UTF_8)); digest.update((byte)0);
        }
        digest.update(home.toString().getBytes(StandardCharsets.UTF_8)); digest.update((byte)0);
        for (Path file : jdkIdentityInputs()) digestFile(digest, file);
        return HexFormat.of().formatHex(digest.digest());
    }
    public static String toolIdentity() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Class<?> tool : List.of(NamespaceMetadataAcceptance.class, MinecraftClassNamespaceProducer.class, MinecraftClassNamespaceProducerMain.class)) {
            digest.update(tool.getName().getBytes(StandardCharsets.UTF_8)); digest.update((byte)0);
            for (Path file : toolPayload(tool)) {
                digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8)); digest.update((byte)0); digestFile(digest, file);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void digestFile(MessageDigest hash, Path file) throws Exception {
        if (!Files.isRegularFile(file)) throw new IllegalStateException("Missing immutable identity input: " + file);
        try (var stream = Files.newInputStream(file)) { byte[] buffer = new byte[65536]; int count; while ((count = stream.read(buffer)) != -1) hash.update(buffer, 0, count); }
        hash.update((byte)0);
    }
    public static String classpathHash(List<Path> classpath) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (Path path : classpath) {
            if (!Files.isRegularFile(path)) throw new IllegalStateException("Metadata acceptance requires immutable jar/file inputs, not native source outputs: " + path);
            hash.update(path.getFileName().toString().getBytes(StandardCharsets.UTF_8)); hash.update((byte)0);
            try (var stream = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int count; while ((count = stream.read(buffer)) != -1) hash.update(buffer, 0, count); }
            hash.update((byte)0);
        }
        return HexFormat.of().formatHex(hash.digest());
    }
}
