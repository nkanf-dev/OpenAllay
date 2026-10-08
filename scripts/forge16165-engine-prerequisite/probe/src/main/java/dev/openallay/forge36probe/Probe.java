package dev.openallay.forge36probe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonToken;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.openallay.OpenAllayConstants;
import dev.openallay.logging.OpenAllayLogger;
import dev.openallay.api.extension.ExtensionDescriptor;
import dev.openallay.api.extension.ExtensionEnvironment;
import dev.openallay.api.extension.OpenAllayExtension;
import dev.openallay.bridge.protocol.ToolExecutionMessage;
import dev.openallay.extension.ExtensionCompatibility;
import dev.openallay.extension.universal.UniversalExtensionSupport;
import dev.openallay.json.EngineJson;
import dev.openallay.json.JsonReaders;
import dev.openallay.json.JsonTrees;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.JavascriptRuntimeLimits;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.host.RhinoHostAdapter;
import dev.openallay.script.schema.HostSchema;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("openallay_engine_probe")
public final class Probe {
    public Probe() { DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> Client::register); }
    public static final class Client {
        public static void register() {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(Client::setup);
        }
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> run(event.getMinecraftSupplier().get()));
        }
        public static void run(Object actualClient) {
            Receipt receipt = null;
            String stage = "identity";
            try {
                receipt = new Receipt();
                receipt.begin(stage);
                check(actualClient != null, "FML real Minecraft client");
                receipt.detail("clientClass", actualClient.getClass().getName());
                receipt.detail("pid", Long.toString(ProcessHandle.current().pid()));
                receipt.detail("javaVersion", System.getProperty("java.runtime.version"));
                check(Runtime.version().feature() == 17, "Java17 runtime");
                receipt.detail("thread", Thread.currentThread().getName());
                for (Class<?> type : List.of(Probe.class, RhinoJavascriptRuntime.class,
                        EngineJson.class, Context.class, OpenAllayExtension.class,
                        OpenAllayConstants.class, OpenAllayLogger.class, Gson.class,
                        com.google.common.collect.ImmutableList.class, org.apache.logging.log4j.Logger.class)) {
                    receipt.identity(type);
                }
                for (Class<?> type : List.of(RhinoJavascriptRuntime.class, EngineJson.class,
                        Context.class, OpenAllayExtension.class, OpenAllayConstants.class,
                        OpenAllayLogger.class)) soleOwner(type);
                check(major(RhinoJavascriptRuntime.class) == 61 && major(Context.class) == 61,
                        "unchanged engine/Rhino Java61");
                check(major(OpenAllayExtension.class) == 52, "SDK Java52");
                for (Class<?> host : List.of(Gson.class, com.google.common.collect.ImmutableList.class,
                        org.apache.logging.log4j.Logger.class)) {
                    check(!archive(host).equals(archive(Probe.class)), "host original code source " + host);
                }
                check(System.Logger.class.getClassLoader() == null
                        && System.Logger.class.getModule() == Object.class.getModule(),
                        "JDK bootstrap System.Logger API");
                JsonObject jdkLogging = new JsonObject();
                jdkLogging.addProperty("class", System.Logger.class.getName());
                jdkLogging.addProperty("loader", "bootstrap");
                jdkLogging.addProperty("module", System.Logger.class.getModule().getName());
                System.Logger backend = System.getLogger(OpenAllayConstants.MOD_NAME);
                jdkLogging.addProperty("name", backend.getName());
                jdkLogging.addProperty("implementationClass", backend.getClass().getName());
                jdkLogging.addProperty("implementationModule", backend.getClass().getModule().getName());
                receipt.detail("jdkLogging", jdkLogging);
                receipt.pass();

                if (!Boolean.getBoolean("oa36.pendingOnly")) {
                stage = "engine-logging"; receipt.begin(stage);
                OpenAllayConstants.LOGGER.info("OA36 ENGINE_LOGGING_INFO engine={} value={}",
                        OpenAllayConstants.MOD_NAME, "brace-ok");
                OpenAllayConstants.LOGGER.warn("OA36 ENGINE_LOGGING_WARN engine={} value={}",
                        OpenAllayConstants.MOD_NAME, "brace-ok");
                OpenAllayConstants.LOGGER.error("OA36 ENGINE_LOGGING_ERROR engine={} value={}",
                        OpenAllayConstants.MOD_NAME, "brace-ok",
                        new IllegalStateException("OA36 ENGINE_LOGGING_THROWABLE"));
                receipt.detail("loggerClass", OpenAllayConstants.LOGGER.getClass().getName());
                receipt.detail("loggingProof", "formatted-info-warning-error-and-throwable");
                receipt.pass();

                stage = "bound-engine-json"; receipt.begin(stage);
                Gson bound = EngineJson.create();
                check(EngineJson.withInstant(bound) == bound, "bound owner identity");
                Instant timestamp = Instant.ofEpochSecond(123, 456);
                check(bound.fromJson(bound.toJson(timestamp), Instant.class).equals(timestamp), "Instant roundtrip");
                for (String malformed : List.of("{\"seconds\":1}",
                        "{\"seconds\":1,\"nanos\":-1}", "{\"seconds\":1,\"nanos\":1.5}",
                        "{\"seconds\":1,\"seconds\":2,\"nanos\":0}",
                        "{\"seconds\":1,\"nanos\":0,\"extra\":1}")) {
                    reject(() -> bound.fromJson(malformed, Instant.class), "strict Instant " + malformed);
                }
                Canonical valid = new Canonical("native-probe", 3, timestamp);
                check(bound.fromJson(bound.toJson(valid), Canonical.class).equals(valid), "canonical record roundtrip");
                reject(() -> bound.fromJson("{\"id\":\"\",\"count\":3}", Canonical.class), "canonical constructor");
                reject(() -> bound.fromJson("{\"id\":\"x\",\"count\":null}", Canonical.class), "primitive null");
                reject(() -> bound.fromJson("{\"id\":\"x\",\"id\":\"y\",\"count\":3}", Canonical.class), "duplicate component");
                receipt.detail("value", bound.toJsonTree(valid)); receipt.pass();

                stage = "json-trees-readers"; receipt.begin(stage);
                JsonObject source = JsonTrees.parse("{\"nested\":{\"value\":1},\"rows\":[1,2]}").getAsJsonObject();
                JsonObject copy = JsonTrees.copy(source);
                source.getAsJsonObject("nested").addProperty("value", 9);
                check(copy.getAsJsonObject("nested").get("value").getAsInt() == 1, "detached nested copy");
                Set<String> keys = JsonTrees.keys(source);
                source.addProperty("live", true); check(keys.contains("live"), "live keys");
                check(keys.remove("live") && !source.has("live"), "live key removal");
                try (var reader = JsonReaders.strict(new StringReader("{\"value\":[1,2]}"))) {
                    check(JsonReaders.elements(JsonReaders.read(reader).getAsJsonObject()
                            .getAsJsonArray("value")).size() == 2, "strict values");
                    check(reader.peek() == JsonToken.END_DOCUMENT, "strict complete input");
                }
                boolean rejected = false;
                try (var reader = JsonReaders.strict(new StringReader("TRUE"))) { JsonReaders.read(reader); }
                catch (com.google.gson.stream.MalformedJsonException expected) { rejected = true; }
                check(rejected, "uppercase strict literal"); receipt.pass();

                stage = "engine-rhino-record-schema"; receipt.begin(stage);
                // Empty, private, never-mutated probe catalog. No bundled-resource loading claim.
                var runtime = new RhinoJavascriptRuntime(Duration.ofSeconds(2),
                        JavascriptRuntimeLimits.DEFAULT, new JavascriptModuleCatalog(Map.of()));
                var value = runtime.execute("return {sum:helpers.sum(mc.row.values),"
                        + "shape:helpers.schema(mc.row,4),hidden:typeof mc.row.getClass};",
                        Map.of("row", new Row("native-probe", List.of(1,2,3))),
                        Map.of(), new CancellationSignal()).value().getAsJsonObject();
                check(value.get("sum").getAsInt() == 6, "record/helper sum");
                check(value.get("hidden").getAsString().equals("undefined"), "hidden getClass");
                check(value.getAsJsonObject("shape").get("id").getAsString().equals("string"), "helper schema");
                HostSchema schema = RhinoHostAdapter.declaredSchema(Row.class);
                check(schema instanceof HostSchema.RecordValue record
                        && record.fields().get("id").kind().equals("string")
                        && record.fields().get("values") instanceof HostSchema.Sequence, "declared record schema");
                receipt.detail("value", value); receipt.detail("moduleCatalog", "empty-probe-only"); receipt.pass();

                }
                stage = "rhino-default-interface-java-adapter"; receipt.begin(stage);
                Context cx = new Context(new ContextFactory());
                dev.latvian.mods.rhino.Scriptable scope = null;
                try {
                    scope = cx.initStandardObjects(null, false);
                    // Current Rhino installs JavaAdapter, not a Packages namespace.
                    dev.latvian.mods.rhino.ScriptableObject.putProperty(scope, "Greeting",
                            new dev.latvian.mods.rhino.NativeJavaClass(cx, scope, Greeting.class), cx);
                    Object adapted = cx.evaluateString(scope,
                            "new JavaAdapter(Greeting, {})",
                            "forge36-adapter", 1, null);
                    Greeting greeting = (Greeting) cx.jsToJava(adapted, TypeInfo.of(Greeting.class));
                    check(greeting.greet().equals("default-ok"), "default-interface dispatch");
                } finally {
                    // This ABI has no exit/close API. No factory.enter() ThreadLocal is created.
                    // No scope, adapter, context, or callback is retained beyond this stage.
                    scope = null;
                    cx = null;
                }
                receipt.pass();

                stage = "existing-tool-envelope-copy"; receipt.begin(stage);
                JsonObject result = new JsonObject(); result.addProperty("status", "ok");
                ToolExecutionMessage message = new ToolExecutionMessage(result, List.of());
                result.addProperty("status", "mutated");
                check(message.result().get("status").getAsString().equals("ok"), "constructor copy");
                JsonObject returned = message.result(); returned.addProperty("status", "mutated-again");
                check(message.result().get("status").getAsString().equals("ok"), "accessor copy"); receipt.pass();

                stage = "builder-descriptor-only"; receipt.begin(stage);
                Class<? extends OpenAllayExtension> builder = Class.forName(
                        "dev.openallay.builder.BuilderExtension", true, Probe.class.getClassLoader())
                        .asSubclass(OpenAllayExtension.class);
                receipt.identity(builder); soleOwner(builder);
                check(major(builder) == 52, "Builder Java52");
                ExtensionDescriptor descriptor = builder.getConstructor().newInstance().descriptor();
                check(descriptor.id().equals("openallay:builder"), "Builder descriptor identity");
                ExtensionEnvironment facts = new ExtensionEnvironment("forge", "1.16.5", "0.4.3",
                        Set.of("0.4.0"), 17, Set.of());
                check(descriptor.support().targets().stream().noneMatch(target ->
                        target.loader().equals("forge") && ExtensionCompatibility.includes(
                                target.minecraftVersionRange(), "1.16.5")), "undeclared Forge16 target");
                check(UniversalExtensionSupport.matchingTarget(descriptor.support(), facts).isEmpty(), "no admission");
                receipt.detail("id", descriptor.id());
                receipt.detail("incompatibility", UniversalExtensionSupport.incompatibility(descriptor.support(), facts));
                // Never contribute/register/install, and never fabricate host features.
                String outcome = Boolean.getBoolean("oa36.pendingOnly") ? "PENDING_STAGES_PASS" : "PASS";
                receipt.pass(); receipt.finish(outcome, null);
                System.out.println(Boolean.getBoolean("oa36.pendingOnly")
                        ? "OA36 PENDING_STAGES_PASS" : "OA36 ENGINE_PREREQUISITE_PASS"); System.out.flush();
            } catch (Throwable failure) {
                failure.printStackTrace(System.err);
                try { if (receipt != null) receipt.finish("FAIL", failure); }
                catch (Throwable writeFailure) { failure.addSuppressed(writeFailure); writeFailure.printStackTrace(System.err); }
                System.err.println("OA36 ENGINE_PREREQUISITE_FAIL stage=" + stage); System.err.flush();
                throw new IllegalStateException("Forge36 engine prerequisite: " + stage, failure);
            }
        }
    }
    public record Row(String id, List<Integer> values) { public Row { values = List.copyOf(values); } }
    public record Canonical(String id, int count, Instant at) {
        public Canonical { if (id == null || id.isBlank() || count < 0) throw new IllegalArgumentException("invalid probe record"); }
    }
    public interface Greeting { default String greet() { return "default-ok"; } }
    private static void reject(Runnable operation, String message) {
        try { operation.run(); } catch (JsonParseException expected) { return; }
        throw new AssertionError("Accepted malformed " + message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static java.net.URL sourceUrl(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource();
        check(source != null && source.getLocation() != null, "class CodeSource " + type.getName());
        return source.getLocation();
    }
    private static Path archive(Class<?> type) throws Exception {
        java.net.URL source = sourceUrl(type);
        Path path;
        boolean owned = type.getName().startsWith("dev.openallay.")
                || type.getName().startsWith("dev.latvian.mods.rhino.");
        if (owned) {
            check(source.getProtocol().equals("modjar"), "owned normal Forge CodeSource " + source);
            // Forge36 CodeSource uses the normal modjar URL handler, not a NIO filesystem.
            check(source.getHost().equals("openallay_engine_probe") && source.getPath().isEmpty()
                    && source.getPort() == -1 && source.getUserInfo() == null
                    && source.getQuery() == null && source.getRef() == null, "sole modjar CodeSource " + source);
            var info = net.minecraftforge.fml.ModList.get().getModFileById(source.getHost());
            check(info != null, "normal Forge mod file " + source.getHost());
            path = info.getFile().getFilePath().toRealPath();
            Path installed = Path.of(System.getProperty("oa36.mod")).toRealPath();
            check(path.equals(installed), "Forge mod file equals collector-installed archive");
        } else {
            check(source.getProtocol().equals("file"), "official host file CodeSource " + source);
            path = Path.of(source.toURI()).toRealPath();
        }
        check(Files.isRegularFile(path), "normal archive code source " + type.getName()); return path;
    }
    private static JsonObject modEntryProof(Class<?> type, Path owner) throws Exception {
        String entry = type.getName().replace('.', '/') + ".class";
        java.net.URL resource = type.getResource("/" + entry);
        check(resource != null && resource.getProtocol().equals("modjar")
                && resource.getHost().equals(sourceUrl(type).getHost())
                && resource.getPath().equals("/" + entry) && resource.getPort() == -1
                && resource.getUserInfo() == null && resource.getQuery() == null && resource.getRef() == null,
                "normal modjar class resource " + type.getName());
        byte[] resourceBytes;
        try (var stream = resource.openStream()) { resourceBytes = stream.readAllBytes(); }
        byte[] archiveBytes;
        try (var jar = new java.util.zip.ZipFile(owner.toFile())) {
            var member = jar.getEntry(entry);
            check(member != null && !member.isDirectory(), "sole archive class entry " + entry);
            try (var stream = jar.getInputStream(member)) { archiveBytes = stream.readAllBytes(); }
        }
        check(java.util.Arrays.equals(resourceBytes, archiveBytes), "normal resource bytes match sole archive " + entry);
        JsonObject proof = new JsonObject();
        proof.addProperty("classResourceURL", resource.toExternalForm());
        proof.addProperty("archiveEntry", entry);
        proof.addProperty("classResourceSha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(resourceBytes)));
        proof.addProperty("archiveEntrySha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(archiveBytes)));
        return proof;
    }
    private static void soleOwner(Class<?> type) throws Exception {
        check(type.getClassLoader() == Probe.class.getClassLoader(), "normal mod loader " + type.getName());
        check(archive(type).equals(archive(Probe.class)), "sole mod archive " + type.getName());
    }
    private static int major(Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            check(stream != null, "class bytes " + type.getName());
            byte[] header = stream.readNBytes(8);
            check(header.length == 8 && header[0] == (byte) 0xca && header[1] == (byte) 0xfe
                    && header[2] == (byte) 0xba && header[3] == (byte) 0xbe, "class header");
            return (header[6] & 255) * 256 + (header[7] & 255);
        }
    }
    private static String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static final class Receipt {
        private final JsonObject root = new JsonObject();
        private final JsonArray stages = new JsonArray();
        private final java.util.Map<Path, String> archiveHashes = new java.util.HashMap<>();
        private JsonObject current;
        private final Path file;
        Receipt() {
            file = Path.of(System.getProperty("oa36.receipt")).toAbsolutePath().normalize();
            check(!Files.exists(file) && Files.isDirectory(file.getParent()), "fresh collector receipt path");
            root.addProperty("status", "RUNNING"); root.addProperty("prerequisiteOnly", true);
            root.addProperty("fullNativeSupport", false); root.add("stages", stages);
        }
        void begin(String stage) throws Exception {
            current = new JsonObject(); current.addProperty("stage", stage); current.addProperty("status", "RUNNING");
            current.add("details", new JsonObject()); stages.add(current); save();
        }
        void detail(String key, String value) { current.getAsJsonObject("details").addProperty(key, value); }
        void detail(String key, com.google.gson.JsonElement value) { current.getAsJsonObject("details").add(key, value); }
        void identity(Class<?> type) throws Exception {
            JsonObject value = new JsonObject();
            value.addProperty("class", type.getName()); value.addProperty("loader", type.getClassLoader().toString());
            value.addProperty("loaderIdentity", System.identityHashCode(type.getClassLoader()));
            value.addProperty("rawCodeSourceURL", sourceUrl(type).toExternalForm());
            detail(type.getName(), value);
            Path owner = archive(type);
            if (sourceUrl(type).getProtocol().equals("modjar")) {
                value.add("modEntryProof", modEntryProof(type, owner));
                value.addProperty("originKind", "forge-modjar");
            } else {
                value.addProperty("originKind", "official-host-file");
            }
            String ownerHash = archiveHashes.get(owner);
            if (ownerHash == null) { ownerHash = hash(owner); archiveHashes.put(owner, ownerHash); }
            value.addProperty("codeSource", owner.toString()); value.addProperty("archiveSha256", ownerHash);
            value.addProperty("classMajor", major(type));
            value.addProperty("implementationVersion", type.getPackage().getImplementationVersion());
            detail(type.getName(), value);
        }
        void pass() throws Exception { current.addProperty("status", "PASS"); save(); }
        void finish(String status, Throwable failure) throws Exception {
            root.addProperty("status", status);
            if (failure != null) {
                if (current != null) current.addProperty("status", "FAIL");
                StringWriter text = new StringWriter(); failure.printStackTrace(new PrintWriter(text));
                root.addProperty("failureStage", current == null ? "receipt-initialization" : current.get("stage").getAsString());
                root.addProperty("fullCause", text.toString());
            }
            save();
        }
        void save() throws Exception {
            String json = new Gson().toJson(root);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, json + "\n");
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("OA36 RECEIPT " + json); System.out.flush();
        }
    }
}
