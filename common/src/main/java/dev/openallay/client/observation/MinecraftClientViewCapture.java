package dev.openallay.client.observation;

import dev.openallay.client.gui.MinecraftClientWindow;

import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

/** Next native frame capture. It never replaces a Screen or changes game input state. */
public final class MinecraftClientViewCapture implements AutoCloseable {
    private static final List<MinecraftClientViewCapture> ACTIVE = new CopyOnWriteArrayList<>();
    private final Minecraft client;
    private final PlatformService platform;
    private final WorldObservationRuntime observations;
    private final String correlationId;
    private final UUID actor;
    private final Object level;
    private final String dimension;
    private final Set<Pending> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public MinecraftClientViewCapture(Minecraft client, PlatformService platform,
            WorldObservationRuntime observations, String correlationId, UUID actor, String dimension) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.observations = java.util.Objects.requireNonNull(observations, "observations");
        this.correlationId = java.util.Objects.requireNonNull(correlationId, "correlationId");
        this.actor = java.util.Objects.requireNonNull(actor, "actor");
        this.dimension = java.util.Objects.requireNonNull(dimension, "dimension");
        this.level = client.level;
        ACTIVE.add(this);
    }

    public CompletableFuture<WorldViewCapture> capture(WorldViewRequest request, CancellationSignal cancellation) {
        CompletableFuture<WorldViewCapture> result = new CompletableFuture<>();
        Pending capture = new Pending(request, cancellation, result);
        pending.add(capture);
        result.whenComplete((value, failure) -> pending.remove(capture));
        cancellation.onCancel(() -> result.completeExceptionally(unavailable("Native view capture was cancelled")));
        try {
            client.execute(() -> {
                try {
                    verify(capture);
                    if (request.target() == WorldViewRequest.Target.ASSOCIATED_UI) {
                        capture.submitted = true;
                        observations.associatedCapture(correlationId, actor).whenComplete((value, failure) -> {
                            try {
                                if (failure != null) result.completeExceptionally(failure);
                                else client.execute(() -> {
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

    /** Exact GameRenderer pre-GuiRenderer invocation hook. One GPU readback serves each target group. */
    public static void beforeGui(Minecraft client, boolean advanceGameTime) {
        if (!client.isGameLoadFinished() || !advanceGameTime || client.level == null) return;
        for (MinecraftClientViewCapture capture : ACTIVE) {
            if (capture.client == client) capture.frame(WorldViewRequest.Target.WORLD);
        }
    }

    /** Exact GameRenderer post-GuiRenderer invocation hook. Native game UI only, never Guide pixels. */
    public static void afterGui(Minecraft client, boolean advanceGameTime) {
        if (!client.isGameLoadFinished() || !advanceGameTime || client.level == null) return;
        for (MinecraftClientViewCapture capture : ACTIVE) {
            if (capture.client == client) capture.frame(WorldViewRequest.Target.GAME_UI);
        }
    }

    public static boolean owns(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("dev.openallay.client.");
    }

    private void frame(WorldViewRequest.Target target) {
        if (closed) return;
        List<Pending> selected = pending.stream().filter(value -> !value.submitted && !value.result.isDone()
                && value.request.target() == target).toList();
        if (selected.isEmpty()) return;
        try {
            for (Pending capture : selected) verify(capture);
            if (target == WorldViewRequest.Target.GAME_UI && owns(MinecraftClientWindow.screen(client))) {
                throw unavailable("OpenAllay is foreground; no native game UI is currently displayed");
            }
            var nativeTarget = MinecraftClientWindow.mainRenderTarget(client);
            var limits = observations.imageLimits(actor);
            if (nativeTarget.width <= 0 || nativeTarget.height <= 0
                    || nativeTarget.width > limits.maxDimension() || nativeTarget.height > limits.maxDimension()
                    || (long) nativeTarget.width * nativeTarget.height > limits.maxPixels()) {
                throw new JavascriptExecutionException("view_image_too_large", "Native frame exceeds image dimensions or pixel limits");
            }
            Instant capturedAt = Instant.now();
            WorldFocusObservation focus = ClientFocusCapture.capture(client, platform, capturedAt);
            var camera = MinecraftCameraFacts.rendered(client, focus.camera());
            Frame frame = new Frame(UUID.randomUUID().toString(), capturedAt, target, nativeTarget.width,
                    nativeTarget.height, MinecraftCameraFacts.guiScale(client),
                    camera, focus.screen(), target == WorldViewRequest.Target.GAME_UI && !MinecraftClientWindow.hudHidden(client),
                    target == WorldViewRequest.Target.GAME_UI && (MinecraftClientWindow.screen(client) != null || MinecraftClientWindow.overlay(client) != null));
            selected.forEach(value -> value.submitted = true);
            Screenshot.takeScreenshot(nativeTarget, image -> nativeReady(frame, selected, image));
        } catch (Throwable failure) {
            selected.forEach(value -> value.result.completeExceptionally(failure));
        }
    }

    /** Native callback must never throw: Screenshot closes its GPU buffer after this callback. */
    private void nativeReady(Frame frame, List<Pending> selected, NativeImage image) {
        try {
            if (selected.stream().noneMatch(value -> available(value))) { image.close(); return; }
            Thread.ofVirtual().name("openallay-native-view-encode").start(() -> encode(frame, selected, image));
        } catch (Throwable failure) {
            try { image.close(); } catch (Throwable ignored) {}
            selected.forEach(value -> value.result.completeExceptionally(failure));
        }
    }

    private void encode(Frame frame, List<Pending> selected, NativeImage image) {
        try {
            int width;
            int height;
            int[] pixels;
            try (image) {
                width = image.getWidth();
                height = image.getHeight();
                pixels = image.getPixels();
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
                            platform.gameVersion(), platform.platformName(), Map.of("minecraft:dimension", dimension,
                                    "openallay:target", frame.target.name(), "openallay:capture", frame.id,
                                    "openallay:layers", frame.target == WorldViewRequest.Target.WORLD
                                            ? "native_world_before_2d_gui" : "actual_visible_game_ui_hud_toasts"));
                    WorldViewCapture result = new WorldViewCapture(frame.id, frame.capturedAt, actor, dimension,
                            frame.target, frame.hud, frame.gameUi, frame.width, frame.height, frame.guiScale,
                            frame.camera, frame.screen, reference, evidence);
                    client.execute(() -> {
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
        return !closed && client.level == level && !capture.cancellation.isCancelled() && !capture.result.isDone();
    }

    private void verify(Pending capture) {
        capture.cancellation.throwIfCancelled();
        if (closed || client.level != level || client.player == null || client.level == null
                || !actor.equals(client.player.getUUID())
                || !dimension.equals(client.level.dimension().identifier().toString())) {
            throw unavailable("Native view source is no longer available");
        }
        if (!client.isSameThread()) throw new IllegalStateException("Native view admission must run on the Minecraft thread");
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

    private record Frame(String id, Instant capturedAt, WorldViewRequest.Target target, int width, int height,
            int guiScale, WorldFocusObservation.Camera camera, WorldFocusObservation.Screen screen,
            boolean hud, boolean gameUi) {}
}
