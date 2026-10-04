package dev.openallay.adapter.minecraft.v26_2.world;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Scans source and class bytes without loading any game class or using fake game classes. */
class AdapterArchitectureTest {
    private static final String SDK = "dev/openallay/api/extension/";
    private static final String OWN = "dev/openallay/adapter/minecraft/v26_2/world/";

    @Test void productionImportsOnlyOwnSdkJdkAndActualNativeLibraries() throws IOException {
        Path sources = Path.of(System.getProperty("minecraft26Adapter.sources"));
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("dev.openallay.builder"), file.toString());
                assertFalse(source.contains("dev.openallay.context"), file.toString());
                assertFalse(source.contains("dev.openallay.extension"), file.toString());
                assertFalse(source.contains("BuilderBounds"), file.toString());
                assertFalse(source.contains("BlockSpec"), file.toString());
                for (String line : source.lines().filter(l -> l.startsWith("import ")).toList()) {
                    String name = line.substring(7).replace("static ", "").replace(";", "");
                    assertTrue(name.startsWith("java.") || name.startsWith("net.minecraft.")
                            || name.startsWith("com.google.gson.") || name.startsWith("com.mojang.")
                            || name.startsWith("dev.openallay.api.extension."), "Unapproved import in " + file + ": " + name);
                }
            }
        }
    }

    @Test void onlyTheTypedFactoryIsPublicAndItCapturesOnlyAtOpen() throws IOException {
        Path sources = Path.of(System.getProperty("minecraft26Adapter.sources"));
        try (var files = Files.walk(sources)) {
            List<Path> publicClasses = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> uncheckedRead(p).contains("public final class ")).toList();
            assertEquals(List.of(sources.resolve(OWN.replace("/", java.io.File.separator)).resolve("Minecraft26WorldAccess.java")), publicClasses);
        }
        String factory = Files.readString(sources.resolve(OWN).resolve("Minecraft26WorldAccess.java"));
        assertTrue(factory.contains("implements MinecraftWorldAccess"));
        assertTrue(factory.contains("public Minecraft26WorldAccess() {}"));
        assertTrue(factory.indexOf("invocation.requireActive()") < factory.indexOf("Minecraft.getInstance()"));
        assertTrue(factory.contains("invocation.onCancel(bridge::close)"));
        assertFalse(factory.contains("static final Minecraft"));
    }

    @Test void compiledConstantPoolsHaveNoDomainOrOldCoreDependency() throws IOException {
        int count = 0;
        for (String directory : System.getProperty("minecraft26Adapter.classes").split(java.io.File.pathSeparator)) {
            Path root = Path.of(directory);
            if (!Files.exists(root)) continue;
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    count++;
                    for (String value : utf8Constants(file)) {
                        int start = 0;
                        while ((start = value.indexOf("dev/openallay/",start)) >= 0) {
                            String name = value.substring(start);
                            assertTrue(name.startsWith(SDK) || name.startsWith(OWN),
                                    "Unexpected internal class reference in " + file + ": " + value);
                            start += "dev/openallay/".length();
                        }
                        assertFalse(value.contains("dev.openallay.builder"), file.toString());
                    }
                }
            }
        }
        assertTrue(count >= 6, "Adapter production classes must exist");
    }

    @Test void buildDoesNotDependOnCoreDomainOrBundleGameLibraries() throws IOException {
        String build = Files.readString(Path.of(System.getProperty("minecraft26Adapter.buildFile")));
        assertTrue(build.contains("id 'java-library'"));
        assertTrue(build.contains("id 'net.neoforged.moddev'"));
        assertTrue(build.contains("compileOnly(project(':extension-api'))"));
        assertTrue(build.contains("options.release = 25"));
        assertFalse(build.contains("project(':common')"));
        assertFalse(build.contains("multiloader"));
        assertFalse(build.contains("jarJar("));
        assertFalse(build.contains("implementation('com.google.code.gson"));
    }

    @Test void allNativeCommitHooksAndOpaqueNbtSerializationRemainActualNativeCalls() throws IOException {
        Path source = Path.of(System.getProperty("minecraft26Adapter.sources")).resolve(OWN);
        String codec = Files.readString(source.resolve("NativeBlockCodec.java"));
        String blockEntityData = Files.readString(source.resolve("NativeBlockEntityData.java"));
        for (String call : List.of("TagValueInput.create", "TagValueOutput.createWithContext",
                "entity.loadWithComponents", "entity.saveWithFullMetadata", "reporter.isEmpty()"))
            assertTrue(blockEntityData.contains(call), "Missing native block-entity behavior: " + call);
        for (String call : List.of("TagParser.parseCompoundFully", "NativeBlockEntityData.load", "NativeBlockEntityData.save",
                "materialPalette()", "encodeState(decode(entry.getValue().toString()))", "level.setBlock(", "level.removeBlockEntity",
                "level.setBlockEntity", "level.blockEntityChanged", "level.sendBlockUpdated", "private static final int WRITE_FLAGS = 18;"))
            assertTrue(codec.contains(call), "Missing native behavior: " + call);
        String session = Files.readString(source.resolve("NativeWorldSession.java"));
        for (String call : List.of("getChunkNow", "hasPrimedHeightmap", "getBlockEntitiesPos", "updateFromNeighbourShapes",
                "updateNeighborsAt", "updateNeighbourForOutputSignal", "NativeBlockCodec.sameImage", "openallay_builder:world_write"))
            assertTrue(session.contains(call), "Missing session behavior: " + call);
        String world = Files.readString(source.resolve("NativeWorldIdentity.java"));
        assertTrue(world.contains("Identifier.fromNamespaceAndPath(\"openallay_builder\",\"world_identity\")"));
        assertTrue(session.contains("config/openallay-builder"));
        assertTrue(session.contains("result.add(\"materialPalette\", NativeBlockCodec.materialPalette())"));
    }

    private static String uncheckedRead(Path path) {
        try { return Files.readString(path); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    private static List<String> utf8Constants(Path path) throws IOException {
        List<String> values = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            assertEquals(0xCAFEBABE, input.readInt());
            input.readUnsignedShort(); input.readUnsignedShort();
            int count = input.readUnsignedShort();
            for (int i = 1; i < count; i++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> values.add(input.readUTF());
                    case 3,4 -> input.skipNBytes(4);
                    case 5,6 -> { input.skipNBytes(8); i++; }
                    case 7,8,16,19,20 -> input.skipNBytes(2);
                    case 9,10,11,12,17,18 -> input.skipNBytes(4);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unknown class constant tag: " + tag);
                }
            }
        }
        return values;
    }
}
