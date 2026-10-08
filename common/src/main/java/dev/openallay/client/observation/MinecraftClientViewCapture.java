package dev.openallay.client.observation;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.context.ClientFocusCapture;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.platform.PlatformService;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.world.WorldFocusObservation;
import dev.openallay.world.WorldObservationRuntime;
import dev.openallay.world.WorldViewCapture;
import dev.openallay.world.WorldViewRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;


/** Next native frame capture. It never replaces a Screen or changes game input state. */
public final class MinecraftClientViewCapture implements AutoCloseable {
    private static final List<MinecraftClientViewCapture> ACTIVE = new CopyOnWriteArrayList<>();
    private final net.minecraft.client.Minecraft client;
    private final PlatformService platform;
    private final WorldObservationRuntime observations;
    private final String correlationId;
    private final UUID actor;
    private final Object level;
    private final String dimension;
    private final Set<Pending> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public MinecraftClientViewCapture(net.minecraft.client.Minecraft client, PlatformService platform,
            WorldObservationRuntime observations, String correlationId, UUID actor, String dimension) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.observations = java.util.Objects.requireNonNull(observations, "observations");
        this.correlationId = java.util.Objects.requireNonNull(correlationId, "correlationId");
        this.actor = java.util.Objects.requireNonNull(actor, "actor");
        this.dimension = java.util.Objects.requireNonNull(dimension, "dimension");
        this.level = dev.openallay.client.context.MinecraftClientContextFacts.world(client);
        ACTIVE.add(this);
    }

    public CompletableFuture<WorldViewCapture> capture(WorldViewRequest request, CancellationSignal cancellation) {
        CompletableFuture<WorldViewCapture> result = new CompletableFuture<>();
        Pending capture = new Pending(request, cancellation, result);
        pending.add(capture);
        result.whenComplete((value, failure) -> pending.remove(capture));
        cancellation.onCancel(() -> result.completeExceptionally(unavailable("Native view capture was cancelled")));
        try {
            dev.openallay.client.context.MinecraftClientContextFacts.execute(client, () -> {
                try {
                    verify(capture);
                    if (request.target() == WorldViewRequest.Target.ASSOCIATED_UI) {
                        capture.submitted = true;
                        observations.associatedCapture(correlationId, actor).whenComplete((value, failure) -> {
                            try {
                                if (failure != null) result.completeExceptionally(failure);
                                else dev.openallay.client.context.MinecraftClientContextFacts.execute(client, () -> {
                                    try { verify(capture); if (available(capture)) result.complete(value); }
                                    catch (Throwable invalidated) { result.completeExceptionally(invalidated); }
                                });
                            } catch (Throwable rejected) { result.completeExceptionally(rejected); }
                        });
                    }
                    if (request.target() == WorldViewRequest.Target.GAME_UI && owns(MinecraftClientWindow.screen(client))) {
                        throw unavailable("OpenAllay is foreground; no native game UI is currently displayed");
                    }
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (Throwable rejected) { result.completeExceptionally(rejected); }
        return result;
    }

    /** Exact native pre-GUI frame hook. One readback serves each target group. */
    public static void beforeGui(net.minecraft.client.Minecraft client, boolean advanceGameTime) {
        if (!dev.openallay.client.gui.GuideNativeWindowState.frameReady(client) || !advanceGameTime || dev.openallay.client.context.MinecraftClientContextFacts.world(client) == null) return;
        for (MinecraftClientViewCapture capture : ACTIVE) {
            if (capture.client == client) capture.frame(WorldViewRequest.Target.WORLD);
        }
    }

    /** Exact native post-final-GUI frame hook. Native game UI only, never Guide pixels. */
    public static void afterGui(net.minecraft.client.Minecraft client, boolean advanceGameTime) {
        if (!dev.openallay.client.gui.GuideNativeWindowState.frameReady(client) || !advanceGameTime || dev.openallay.client.context.MinecraftClientContextFacts.world(client) == null) return;
        for (MinecraftClientViewCapture capture : ACTIVE) {
            if (capture.client == client) capture.frame(WorldViewRequest.Target.GAME_UI);
        }
    }

    public static boolean owns(Object screen) {
        return screen != null && screen.getClass().getName().startsWith("dev.openallay.client.");
    }

    private void frame(WorldViewRequest.Target target) {
        if (closed) return;
        List<Pending> selected = dev.openallay.util.Java8Collections.toList(pending.stream().filter(value -> !value.submitted && !value.result.isDone()
                && value.request.target() == target));
        if (selected.isEmpty()) return;
        try {
            for (Pending capture : selected) verify(capture);
            if (target == WorldViewRequest.Target.GAME_UI && owns(MinecraftClientWindow.screen(client))) {
                throw unavailable("OpenAllay is foreground; no native game UI is currently displayed");
            }
            dev.openallay.model.image.ImageInputLimits limits = observations.imageLimits(actor);
            if (MinecraftNativeImageCapture.width(client) <= 0 || MinecraftNativeImageCapture.height(client) <= 0
                    || MinecraftNativeImageCapture.width(client) > limits.maxDimension() || MinecraftNativeImageCapture.height(client) > limits.maxDimension()
                    || (long) MinecraftNativeImageCapture.width(client) * MinecraftNativeImageCapture.height(client) > limits.maxPixels()) {
                throw new JavascriptExecutionException("view_image_too_large", "Native frame exceeds image dimensions or pixel limits");
            }
            Instant capturedAt = Instant.now();
            WorldFocusObservation focus = ClientFocusCapture.capture(client, platform, capturedAt);
            dev.openallay.world.WorldFocusObservation.Camera camera = MinecraftCameraFacts.rendered(client, focus.camera());
            Frame frame = new Frame(UUID.randomUUID().toString(), capturedAt, target, MinecraftNativeImageCapture.width(client),
                    MinecraftNativeImageCapture.height(client), MinecraftCameraFacts.guiScale(client),
                    camera, focus.screen(), target == WorldViewRequest.Target.GAME_UI && !MinecraftClientWindow.hudHidden(client),
                    target == WorldViewRequest.Target.GAME_UI && (MinecraftClientWindow.screen(client) != null || dev.openallay.client.context.MinecraftFocusNativeFacts.overlay(client) != null));
            selected.forEach(value -> value.submitted = true);
            MinecraftNativeImageCapture.capture(client).whenComplete((image, failure) -> {
                if (failure != null) selected.forEach(value -> value.result.completeExceptionally(failure));
                else nativeReady(frame, selected, image);
            });
        } catch (Throwable failure) {
            selected.forEach(value -> value.result.completeExceptionally(failure));
        }
    }

    /** The readback future transfers image ownership here; this handler must never throw. */
    private void nativeReady(Frame frame, List<Pending> selected, GuideImageBitmap image) {
        try {
            if (selected.stream().noneMatch(value -> available(value))) { image.close(); return; }
            dev.openallay.concurrent.NamedThreads.startDaemon("openallay-native-view-encode", () -> encode(frame, selected, image));
        } catch (Throwable failure) {
            try { image.close(); } catch (Throwable ignored) {}
            selected.forEach(value -> value.result.completeExceptionally(failure));
        }
    }

    private void encode(Frame frame, List<Pending> selected, GuideImageBitmap image) {
        try {
            int width;
            int height;
            int[] pixels;
            try (image) {
                width = image.width();
                height = image.height();
                pixels = MinecraftImagePixels.argb(image);
            }
            if (selected.stream().noneMatch(this::available)) return;
            BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            bitmap.setRGB(0, 0, width, height, pixels, 0, width);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(png)) {
                if (!ImageIO.write(bitmap, "png", output)) throw new java.io.IOException("PNG encoder is unavailable");
            }
            if (png.size() > observations.imageLimits(actor).maxByteSize()) {
                throw new JavascriptExecutionException("view_image_too_large", "Native PNG exceeds the image byte limit");
            }
            if (selected.stream().noneMatch(this::available)) return;
            observations.captureImport(correlationId, actor, png.toByteArray(),
                    () -> selected.stream().anyMatch(this::available)).whenComplete((reference, failure) -> {
                try {
                    if (failure != null) {
                        selected.forEach(value -> value.result.completeExceptionally(failure));
                        return;
                    }
                    EvidenceMetadata evidence = new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE, DataCompleteness.PARTIAL,
                            frame.capturedAt, "minecraft:client_view", "minecraft:native_render_target",
                            platform.gameVersion(), platform.platformName(), dev.openallay.util.Java8Collections.mapOf("minecraft:dimension", dimension, "openallay:target", frame.target.name(), "openallay:capture", frame.id, "openallay:layers", frame.target == WorldViewRequest.Target.WORLD
                                            ? "native_world_before_2d_gui" : "actual_visible_game_ui_hud_toasts"));
                    WorldViewCapture result = new WorldViewCapture(frame.id, frame.capturedAt, actor, dimension,
                            frame.target, frame.hud, frame.gameUi, frame.width, frame.height, frame.guiScale,
                            frame.camera, frame.screen, reference, evidence);
                    dev.openallay.client.context.MinecraftClientContextFacts.execute(client, () -> {
                        for (Pending capture : selected) {
                            try {
                                verify(capture);
                                if (available(capture)) capture.result.complete(result);
                            } catch (Throwable invalidated) { capture.result.completeExceptionally(invalidated); }
                        }
                    });
                } catch (Throwable rejected) {
                    selected.forEach(value -> value.result.completeExceptionally(rejected));
                }
            });
        } catch (Throwable failure) {
            selected.forEach(value -> value.result.completeExceptionally(failure));
        }
    }

    private boolean available(Pending capture) {
        return !closed && dev.openallay.client.context.MinecraftClientContextFacts.world(client) == level && !capture.cancellation.isCancelled() && !capture.result.isDone();
    }

    private void verify(Pending capture) {
        capture.cancellation.throwIfCancelled();
        if (closed || dev.openallay.client.context.MinecraftClientContextFacts.world(client) != level || client.player == null || dev.openallay.client.context.MinecraftClientContextFacts.world(client) == null
                || !actor.equals(dev.openallay.client.context.MinecraftClientContextFacts.uuid(client.player))
                || !dimension.equals(dev.openallay.client.context.MinecraftClientContextFacts.dimension(client))) {
            throw unavailable("Native view source is no longer available");
        }
        if (!dev.openallay.client.context.MinecraftClientContextFacts.ownerThread(client)) throw new IllegalStateException("Native view admission must run on the Minecraft thread");
    }

    @Override public void close() {
        closed = true;
        ACTIVE.remove(this);
        pending.forEach(value -> value.result.completeExceptionally(unavailable("Native view request was closed")));
        pending.clear();
    }

    private static JavascriptExecutionException unavailable(String message) {
        return new JavascriptExecutionException("client_view_unavailable", message);
    }

    private static final class Pending {
        final WorldViewRequest request;
        final CancellationSignal cancellation;
        final CompletableFuture<WorldViewCapture> result;
        volatile boolean submitted;
        Pending(WorldViewRequest request, CancellationSignal cancellation, CompletableFuture<WorldViewCapture> result) {
            this.request = java.util.Objects.requireNonNull(request, "request");
            this.cancellation = java.util.Objects.requireNonNull(cancellation, "cancellation");
            this.result = result;
        }
    }

    @dev.openallay.value.ValueType(Frame.ValueSchemaProvider.class)
private static final class Frame {
    private final String id;
    private final Instant capturedAt;
    private final WorldViewRequest.Target target;
    private final int width;
    private final int height;
    private final int guiScale;
    private final WorldFocusObservation.Camera camera;
    private final WorldFocusObservation.Screen screen;
    private final boolean hud;
    private final boolean gameUi;
    private Frame(String id, Instant capturedAt, WorldViewRequest.Target target, int width, int height, int guiScale, WorldFocusObservation.Camera camera, WorldFocusObservation.Screen screen, boolean hud, boolean gameUi) {
        this.id = id;
        this.capturedAt = capturedAt;
        this.target = target;
        this.width = width;
        this.height = height;
        this.guiScale = guiScale;
        this.camera = camera;
        this.screen = screen;
        this.hud = hud;
        this.gameUi = gameUi;
    }
    public String id() { return id; }
    public Instant capturedAt() { return capturedAt; }
    public WorldViewRequest.Target target() { return target; }
    public int width() { return width; }
    public int height() { return height; }
    public int guiScale() { return guiScale; }
    public WorldFocusObservation.Camera camera() { return camera; }
    public WorldFocusObservation.Screen screen() { return screen; }
    public boolean hud() { return hud; }
    public boolean gameUi() { return gameUi; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Frame)) return false;
        Frame that = (Frame) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(target, that.target) && width == that.width && height == that.height && guiScale == that.guiScale && java.util.Objects.equals(camera, that.camera) && java.util.Objects.equals(screen, that.screen) && hud == that.hud && gameUi == that.gameUi;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Integer.hashCode(guiScale);
        hash = 31 * hash + java.util.Objects.hashCode(camera);
        hash = 31 * hash + java.util.Objects.hashCode(screen);
        hash = 31 * hash + Boolean.hashCode(hud);
        hash = 31 * hash + Boolean.hashCode(gameUi);
        return hash;
    }
    @Override public String toString() { return "Frame[id=" + id + ", capturedAt=" + capturedAt + ", target=" + target + ", width=" + width + ", height=" + height + ", guiScale=" + guiScale + ", camera=" + camera + ", screen=" + screen + ", hud=" + hud + ", gameUi=" + gameUi + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Frame> schema() {
            return new dev.openallay.value.ValueSchema<>(Frame.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Frame>>asList(new dev.openallay.value.ValueSchema.Component<>(Frame.class, "id", Frame::id), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "capturedAt", Frame::capturedAt), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "target", Frame::target), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "width", Frame::width), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "height", Frame::height), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "guiScale", Frame::guiScale), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "camera", Frame::camera), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "screen", Frame::screen), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "hud", Frame::hud), new dev.openallay.value.ValueSchema.Component<>(Frame.class, "gameUi", Frame::gameUi)), arguments -> new Frame((String) arguments[0], (Instant) arguments[1], (WorldViewRequest.Target) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (WorldFocusObservation.Camera) arguments[6], (WorldFocusObservation.Screen) arguments[7], (Boolean) arguments[8], (Boolean) arguments[9]));
        }
    }
}
}
