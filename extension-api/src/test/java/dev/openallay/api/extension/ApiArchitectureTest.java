package dev.openallay.api.extension;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Checks compiled production descriptors, not just text comments or an import allowlist. */
class ApiArchitectureTest {
    private static final String OWN = "dev/openallay/api/extension/";
    private static final String[] PUBLIC_TYPES = {
        "OpenAllayExtension", "ExtensionDescriptor", "SupportDeclaration", "SupportTarget",
        "ExtensionRequirements", "ExtensionHost", "ExtensionEnvironment", "ExtensionContribution",
        "JavascriptModuleSource", "SkillSource", "ResultViewDeclaration", "ExtensionCapability",
        "JavascriptHostBinding", "JavascriptHostMethod", "JavascriptHostValueType",
        "JavascriptInvocationParticipant", "ExtensionInvocation", "ExtensionEvidence",
        "MinecraftWorldAccess", "WorldSession", "ExtensionException"
    };
    @Test void exactTopLevelPublicClosureAndFinalValueTypes() throws Exception {
        Set<String> expected = new HashSet<String>(Arrays.asList(PUBLIC_TYPES));
        Set<String> actual = new HashSet<String>();
        for (Path file : classFiles()) {
            String name = file.getFileName().toString();
            if (name.contains("$")) continue;
            Class<?> type = Class.forName("dev.openallay.api.extension." + name.substring(0, name.length() - 6));
            if (!Modifier.isPublic(type.getModifiers())) continue;
            actual.add(type.getSimpleName());
            if (!type.isInterface()) assertTrue(Modifier.isFinal(type.getModifiers()), type.getName());
        }
        assertEquals(expected, actual);
    }
    @Test void allProductionClassesAreMajor52AndHaveOnlyJdkOrSdkReferences() throws Exception {
        List<Path> classes = classFiles(); assertFalse(classes.isEmpty());
        for (Path file : classes) {
            try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
                assertEquals(0xCAFEBABE, in.readInt(), file.toString());
                assertEquals(0, in.readUnsignedShort(), file.toString());
                assertEquals(52, in.readUnsignedShort(), file.toString());
                int count = in.readUnsignedShort(); String[] utf8 = new String[count];
                List<Integer> classNames = new ArrayList<Integer>();
                for (int i = 1; i < count; i++) {
                    switch (in.readUnsignedByte()) {
                        case 1: utf8[i] = in.readUTF(); break;
                        case 3: case 4: in.readInt(); break;
                        case 5: case 6: in.readLong(); i++; break;
                        case 7: classNames.add(in.readUnsignedShort()); break;
                        case 8: case 16: in.readUnsignedShort(); break;
                        case 9: case 10: case 11: case 12: case 18:
                            in.readUnsignedShort(); in.readUnsignedShort(); break;
                        case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                        default: fail("Unexpected constant-pool tag in Java-8 SDK: " + file);
                    }
                }
                for (Integer index : classNames) assertAllowedClass(utf8[index], file);
                // Fields/method signatures can mention classes without a CONSTANT_Class entry.
                for (String value : utf8) {
                    if (value == null) continue;
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("L([a-zA-Z0-9_$/]+)[;<]").matcher(value);
                    while (matcher.find()) assertAllowedClass(matcher.group(1), file);
                }
            }
        }
    }
    @Test void sourcesDoNotUseModernJavaOrNativeCoreDependencies() throws Exception {
        Path root = Paths.get(System.getProperty("extensionApi.sources"));
        try (Stream<Path> files = Files.walk(root)) {
            Iterator<Path> iterator = files.filter(p -> p.toString().endsWith(".java")).iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next(); String source = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
                for (String forbidden : Arrays.asList("net.minecraft", "net.fabricmc", "net.minecraftforge", "net.neoforged",
                        "com.google.gson", "org.mozilla", "dev.latvian", "dev.openallay.extension", "dev.openallay.context",
                        "dev.openallay.model", "dev.openallay.ui", "List.of(", "Set.of(", "Map.of(", ".copyOf(", "Path.of(",
                        ".isBlank(", ".strip(", "Thread.ofVirtual(", " record ", " sealed ", " var "))
                    assertFalse(source.contains(forbidden), file + " contains " + forbidden);
            }
        }
    }
    @Test void sdkHasNoGrantMutationOrGenericServiceLocator() throws Exception {
        Set<String> invocation = new HashSet<String>();
        for (java.lang.reflect.Method method : ExtensionInvocation.class.getDeclaredMethods()) invocation.add(method.getName());
        assertEquals(new HashSet<String>(Arrays.asList("extensionId", "correlationId", "capturedAt", "callerKind", "callerUuid",
                "playerDimension", "requireActive", "isCancelled", "onCancel", "hasCapability", "requireCapability",
                "completedSuccessfully", "recordEvidence")), invocation);
        assertEquals(Void.TYPE, ExtensionInvocation.class.getMethod("onCancel", Runnable.class).getReturnType());
        Set<String> host = new HashSet<String>();
        for (java.lang.reflect.Method method : ExtensionHost.class.getDeclaredMethods()) host.add(method.getName());
        assertEquals(new HashSet<String>(Arrays.asList("environment", "minecraftWorldAccess")), host);
        assertEquals(String.class, JavascriptHostMethod.Invoker.class.getMethod("invoke", ExtensionInvocation.class, List.class).getReturnType());
        assertEquals(WorldSession.WriteOutcome.class, WorldSession.class.getMethod("write", int.class, int.class, int.class, String.class).getReturnType());
        assertEquals(Void.TYPE, WorldSession.class.getMethod("close").getReturnType());
        assertEquals(0, WorldSession.class.getMethod("close").getExceptionTypes().length);
        assertEquals(new HashSet<ExtensionInvocation.CallerKind>(Arrays.asList(ExtensionInvocation.CallerKind.CONSOLE, ExtensionInvocation.CallerKind.PLAYER)),
                new HashSet<ExtensionInvocation.CallerKind>(Arrays.asList(ExtensionInvocation.CallerKind.values())));
    }
    private static void assertAllowedClass(String name, Path file) {
        if (name.startsWith("[")) {
            int at = name.indexOf('L');
            if (at < 0) return;
            name = name.substring(at + 1, name.length() - 1);
        }
        assertTrue(name.startsWith(OWN) || name.startsWith("java/lang/") || name.startsWith("java/util/")
                || name.startsWith("java/time/") || name.startsWith("java/nio/file/"),
                file + " has non-SDK/non-JDK or out-of-scope dependency " + name);
    }
    private static List<Path> classFiles() throws IOException {
        List<Path> result = new ArrayList<Path>();
        for (String entry : System.getProperty("extensionApi.classes").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path root = Paths.get(entry); if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".class")).forEach(result::add);
            }
        }
        return result;
    }
}
