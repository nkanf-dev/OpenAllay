package dev.openallay.guide.e2e;

import com.google.gson.JsonObject;
import dev.openallay.OpenAllayBootstrap;
import dev.openallay.api.extension.ExtensionEvidence;
import dev.openallay.api.extension.ExtensionException;
import dev.openallay.api.extension.ExtensionInvocation;
import dev.openallay.api.extension.WorldSession;
import dev.openallay.json.JsonTrees;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Explicit disposable-world SDK acceptance. No model, permission bypass or Builder admission. */
final class GuideNativeWorldAccessProbe {
    private GuideNativeWorldAccessProbe() {}
    static void run(UUID actor, String world, Consumer<Map<String, Object>> finished) {
        String phase = System.getProperty("openallay.e2e.worldPhase", "");
        if (phase.equals("persist") || phase.equals("reload")) { persistence(actor, world, finished); return; }
        Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        var server = dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client);
        if (!Boolean.getBoolean(GuideClientE2EConfig.ENABLED) || server == null || dev.openallay.server.NativeServerOwner.published(server)
                || !world.matches("openallay-builder-[a-zA-Z0-9_.-]+")
                || !world.equals(System.getProperty("openallay.e2e.createWorld", ""))
                || !world.equals(dev.openallay.server.NativeServerOwner.worldName(server))
                || GuideProbeWorldSettings.commandsAllowed(server)
                || client.player == null || !actor.equals(dev.openallay.client.gui.MinecraftClientWindow.actor(client))) {
            throw new IllegalStateException("World SDK probe requires its fresh isolated commands-off world");
        }
        String dimension = dev.openallay.world.MinecraftWorldObservationFacts.dimension(dev.openallay.client.MinecraftLocalPlayerLevel.get(client.player));
        int x = (int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.x(client.player)) + 6;
        int z = (int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.z(client.player)) + 6;
        int y = Math.max(5, Math.min(250, (int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.y(client.player)) + 3));
        var access = OpenAllayBootstrap.initialize().platform().minecraftWorldAccess().orElseThrow();
        Thread worker = new Thread(() -> {
            Map<String,Object> report = new LinkedHashMap<>();
            List<Map<String,Object>> checks = new ArrayList<>();
            report.put("scenario", "native-world-sdk"); report.put("world", world); report.put("actor", actor.toString());
            report.put("dimension", dimension); report.put("checks", checks); report.put("modelUsed", false);
            Invocation invocation = new Invocation(actor, dimension);
            try (WorldSession session = access.open(invocation)) {
                JsonObject context = JsonTrees.parse(session.context()).getAsJsonObject();
                require(context.get("minY").getAsInt()==0 && context.get("maxY").getAsInt()==256, "actual vertical bounds");
                require(!context.getAsJsonObject("materialPalette").has("lightning_rod_up"), "honest unavailable role");
                checks.add(Map.of("check","context","status","PASS","native",context));
                require(session.existingWorldId().isEmpty(), "fresh identity read must not create");
                String worldId = session.worldId(); UUID.fromString(worldId);
                require(session.existingWorldId().orElseThrow().equals(worldId), "identity readback");
                report.put("worldId", worldId);
                checks.add(Map.of("check","native-world-identity","status","PASS"));
                String[] before = session.call(() -> new String[]{session.read(x,y,z),session.read(x+1,y,z)});
                try {
                    session.call(() -> {
                        require(session.isOwnerThread(), "server owner action");
                        session.validatePosition(x,y,z);
                        String stone="{\"id\":\"minecraft:stone\"}";
                        String preview=session.preview(x,y,z,stone);
                        require(session.read(x,y,z).equals(before[0]), "preview did not place");
                        WorldSession.WriteOutcome write=session.write(x,y,z,preview);
                        require(write.failure()==null && write.changed() && preview.equals(write.actual()), "actual stone write/readback");
                        WorldSession.WriteOutcome unchanged=session.write(x,y,z,preview);
                        require(unchanged.failure()==null && !unchanged.changed(), "native no-op accounting");
                        checks.add(Map.of("check","preview-write-readback","status","PASS"));
                        String chest="{\"id\":\"minecraft:chest\",\"properties\":{\"facing\":\"north\",\"type\":\"single\",\"waterlogged\":\"false\"},\"blockEntity\":\"{id:'minecraft:chest',Items:[{Slot:0b,id:'minecraft:diamond',Count:3b}]}\"}";
                        String intended=session.preview(x+1,y,z,chest);
                        require(session.read(x+1,y,z).equals(before[1]), "detached chest preview did not place");
                        WorldSession.WriteOutcome installed=session.write(x+1,y,z,intended);
                        require(installed.failure()==null && installed.changed() && intended.equals(installed.actual()), "full native chest image");
                        require(installed.actual().contains("minecraft:diamond"), "native container Items retained");
                        String rotated=session.transform(installed.actual(),90,"none");
                        require(JsonTrees.parse(rotated).getAsJsonObject().getAsJsonObject("properties").get("facing").getAsString().equals("east"), "native rotate");
                        checks.add(Map.of("check","detached-container-install-transform","status","PASS","actual",installed.actual()));
                        try { session.preview(x,y,z,"{\"id\":\"minecraft:does_not_exist\"}"); throw new IllegalStateException("unknown ID accepted"); }
                        catch (IllegalArgumentException | ExtensionException expected) {}
                        require(session.read(x,y,z).equals(preview), "failed preview preserved world");
                        try { session.validatePosition(x,256,z); throw new IllegalStateException("outside height accepted"); }
                        catch (ExtensionException expected) { require(expected.code().equals("invalid_bounds"), "height error classification"); }
                        checks.add(Map.of("check","strict-ID-and-bounds","status","PASS"));
                        session.notifyNeighbours(x+1,y,z);
                        return null;
                    });
                } finally {
                    session.call(() -> {
                        for (int i=0;i<2;i++) {
                            WorldSession.WriteOutcome restored=session.write(x+i,y,z,before[i]);
                            require(restored.failure()==null && before[i].equals(restored.actual()), "native restored image");
                        }
                        return null;
                    });
                }
                checks.add(Map.of("check","original-native-images-restored","status","PASS"));
                invocation.cancel();
                try { session.context(); throw new IllegalStateException("revoked session accepted"); }
                catch (ExtensionException expected) { checks.add(Map.of("check","revocation","status","PASS","code",expected.code())); }
                report.put("outcome","COMPLETED");
            } catch (RuntimeException | Error failure) {
                report.put("outcome","HARNESS_FAILED"); report.put("failure",failure.toString());
                report.put("stack",java.util.Arrays.stream(failure.getStackTrace()).map(Object::toString).toList());
                if (failure.getCause()!=null) report.put("cause",failure.getCause().toString());
            }
            dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> finished.accept(report));
        }, "openallay-native-world-sdk-probe");
        worker.setDaemon(true); worker.start();
    }
    private static void persistence(UUID actor, String world, Consumer<Map<String,Object>> finished) {
        Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        String phase = System.getProperty("openallay.e2e.worldPhase", "");
        boolean reload = phase.equals("reload");
        var server = dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client);
        String requested = System.getProperty(reload ? "openallay.e2e.resumeWorld" : "openallay.e2e.createWorld", "");
        if (!Boolean.getBoolean(GuideClientE2EConfig.ENABLED) || server == null || dev.openallay.server.NativeServerOwner.published(server)
                || !world.equals(requested) || !world.matches("openallay-builder-[a-zA-Z0-9_.-]+")
                || !world.equals(dev.openallay.server.NativeServerOwner.worldName(server)) || GuideProbeWorldSettings.commandsAllowed(server)
                || client.player == null || !actor.equals(dev.openallay.client.gui.MinecraftClientWindow.actor(client))) {
            throw new IllegalStateException("Persistence probe requires its explicitly owned isolated world");
        }
        String dimension = dev.openallay.world.MinecraftWorldObservationFacts.dimension(dev.openallay.client.MinecraftLocalPlayerLevel.get(client.player));
        int freshX=(int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.x(client.player))+5, freshY=Math.max(5,Math.min(250,(int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.y(client.player))+3)), freshZ=(int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.z(client.player))+5;
        var access=OpenAllayBootstrap.initialize().platform().minecraftWorldAccess().orElseThrow();
        java.nio.file.Path retained=dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("config/openallay/e2e/native-world-persistence.json");
        Thread worker=new Thread(() -> {
            Map<String,Object> report=new LinkedHashMap<>();
            report.put("scenario","native-world-sdk");report.put("phase",phase);report.put("world",world);report.put("modelUsed",false);
            try (WorldSession session=access.open(new Invocation(actor,dimension))) {
                if (!reload) {
                    require(session.existingWorldId().isEmpty(),"fresh persistence identity read");
                    String id=session.worldId();
                    String before=session.call(() -> session.read(freshX,freshY,freshZ));
                    String actual=session.call(() -> {
                        String state="{\"id\":\"minecraft:chest\",\"properties\":{\"facing\":\"north\",\"type\":\"single\",\"waterlogged\":\"false\"},\"blockEntity\":\"{id:'minecraft:chest',Items:[{Slot:0b,id:'minecraft:diamond',Count:3b}]}\"}";
                        String intended=session.preview(freshX,freshY,freshZ,state);
                        require(session.read(freshX,freshY,freshZ).equals(before),"persistence preview did not write");
                        WorldSession.WriteOutcome result=session.write(freshX,freshY,freshZ,intended);
                        require(result.failure()==null && result.changed() && intended.equals(result.actual()),"persistence native chest write");
                        return result.actual();
                    });
                    JsonObject receipt=new JsonObject();receipt.addProperty("world",world);receipt.addProperty("worldId",id);
                    receipt.addProperty("x",freshX);receipt.addProperty("y",freshY);receipt.addProperty("z",freshZ);
                    receipt.addProperty("before",before);receipt.addProperty("actual",actual);
                    java.nio.file.Files.createDirectories(retained.getParent());
                    java.nio.file.Files.writeString(retained,receipt.toString(),java.nio.charset.StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE);
                    report.put("worldId",id);report.put("actual",actual);report.put("saveAdmission","Native client shutdown owns game/chunk/SavedData save; reload must prove persisted bytes");
                } else {
                    JsonObject receipt=JsonTrees.parse(java.nio.file.Files.readString(retained,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    require(world.equals(receipt.get("world").getAsString()),"retained world receipt");
                    String id=session.existingWorldId().orElseThrow(() -> new IllegalStateException("Native identity missing after reload"));
                    require(id.equals(receipt.get("worldId").getAsString()),"native UUID persisted across new process");
                    int x=receipt.get("x").getAsInt(),y=receipt.get("y").getAsInt(),z=receipt.get("z").getAsInt();
                    String actual=session.call(() -> session.read(x,y,z));
                    require(actual.equals(receipt.get("actual").getAsString()),"complete native container image persisted across reload");
                    session.call(() -> {
                        WorldSession.WriteOutcome restored=session.write(x,y,z,receipt.get("before").getAsString());
                        require(restored.failure()==null && receipt.get("before").getAsString().equals(restored.actual()),"restore pre-probe image after reload");
                        return null;
                    });
                    report.put("worldId",id);report.put("persistedActual",actual);report.put("originalImageRestored",true);
                }
                report.put("outcome","COMPLETED");
            } catch (Exception | Error failure) {
                report.put("outcome","HARNESS_FAILED");report.put("failure",failure.toString());
                report.put("stack",java.util.Arrays.stream(failure.getStackTrace()).map(Object::toString).toList());
                if(failure.getCause()!=null)report.put("cause",failure.getCause().toString());
            }
            dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> finished.accept(report));
        },"openallay-native-world-persistence-probe");worker.setDaemon(true);worker.start();
    }
    private static void require(boolean value, String check) { if (!value) throw new IllegalStateException(check); }
    private static final class Invocation implements ExtensionInvocation {
        private final UUID actor; private final String dimension; private volatile boolean cancelled;
        private final List<Runnable> listeners = new ArrayList<>();
        Invocation(UUID actor,String dimension) { this.actor=actor;this.dimension=dimension; }
        public String extensionId(){return "openallay:native-world-probe";}
        public String correlationId(){return "native-world-sdk";}
        public Instant capturedAt(){return Instant.now();}
        public CallerKind callerKind(){return CallerKind.PLAYER;}
        public UUID callerUuid(){return actor;}
        public Optional<String> playerDimension(){return Optional.of(dimension);}
        public void requireActive(){if(cancelled)throw new ExtensionException("session_closed","Native probe invocation revoked");}
        public boolean isCancelled(){return cancelled;}
        public synchronized void onCancel(Runnable listener){if(cancelled)listener.run();else listeners.add(listener);}
        synchronized void cancel(){cancelled=true;listeners.forEach(Runnable::run);listeners.clear();}
        public boolean completedSuccessfully(){return !cancelled;}
        public void recordEvidence(ExtensionEvidence evidence){}
    }
}
