package dev.openallay.client.context;

import dev.openallay.client.gui.MinecraftClientWindow;

import com.google.gson.JsonObject;
import dev.openallay.client.gui.mixin.AbstractContainerScreenObservationAccessor;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.platform.PlatformService;
import dev.openallay.world.WorldFocusObservation;
import dev.openallay.world.WorldPosition;
import java.time.Instant;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Reads only public native focus data and a live slot hit-test, without changing client UI state. */
public final class ClientFocusCapture {
    private static final int MAX_DIAGNOSTIC_LENGTH = 240;

    private ClientFocusCapture() {}

    public static WorldFocusObservation capture(Minecraft client, PlatformService platform) {
        requireOwnerThread(client);
        return capture(client, platform, Instant.now());
    }

    /**
     * Sample with the caller's timestamp (for example, the current source frame's sample time).
     * This always reads current native state; it cannot reconstruct an earlier observation.
     */
    public static WorldFocusObservation capture(
            Minecraft client, PlatformService platform, Instant capturedAt) {
        requireOwnerThread(client);
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (client.player == null || client.level == null) {
            throw new IllegalStateException("No active client player or level");
        }

        var player = client.player;
        String dimension = dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(client.level.dimension()).toString();
        Screen nativeScreen = MinecraftClientWindow.screen(client);
        boolean overlay = MinecraftClientWindow.overlay(client) != null;
        WorldFocusObservation.Menu menu = menu(client, nativeScreen, overlay);
        WorldFocusObservation.Hover hover = hover(client, nativeScreen, overlay);
        TreeMap<String, String> details = new TreeMap<>();
        details.put("minecraft:dimension", dimension);
        details.put("minecraft:camera_source", "minecraft:main_camera");
        details.put("minecraft:target_source", "minecraft:client_hit_result");
        details.put("minecraft:component_scope", dev.openallay.context.minecraft.MinecraftItemDataFacts.persistentScope());
        details.put("minecraft:menu_scope", "identity_carried_and_slot_count;slot_contents_not_scanned");
        if (overlay) {
            details.put("minecraft:overlay_class", MinecraftClientWindow.overlay(client).getClass().getName());
        }
        return new WorldFocusObservation(
                capturedAt,
                player.getUUID(),
                dimension,
                camera(client),
                target(client),
                item(client, player.getMainHandItem()),
                item(client, player.getOffhandItem()),
                screen(client, nativeScreen, overlay),
                menu,
                hover,
                new EvidenceMetadata(
                        DataAuthority.CLIENT_VISIBLE,
                        DataCompleteness.PARTIAL,
                        capturedAt,
                        "minecraft:client_focus",
                        "minecraft:client_focus_observation",
                        platform.gameVersion(),
                        platform.platformName(),
                        details));
    }

    /** Current native main-camera numbers, never the player's body rotation or configured FOV. */
    public static WorldFocusObservation.Camera camera(Minecraft client) {
        requireOwnerThread(client);
        return dev.openallay.client.observation.MinecraftCameraFacts.focus(client);
    }

    private static WorldFocusObservation.Target target(Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit == null) {
            return new WorldFocusObservation.Target("none", null, null, null);
        }
        WorldFocusObservation.Position position = position(hit.getLocation());
        // Native MISS is itself a BlockHitResult; test the kind before interpreting block data.
        if (hit.getType() == HitResult.Type.MISS) {
            return new WorldFocusObservation.Target("miss", position, null, null);
        }
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos blockPos = blockHit.getBlockPos();
            var state = client.level.getBlockState(blockPos);
            TreeMap<String, String> properties = new TreeMap<>();
            properties.putAll(dev.openallay.context.minecraft.MinecraftBlockStateProperties.capture(state));
            var fluid = state.getFluidState();
            return new WorldFocusObservation.Target(
                    "block",
                    position,
                    new WorldFocusObservation.Block(
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                            blockPosition(blockPos),
                            blockHit.getDirection().getName(),
                            blockHit.isInside(),
                            dev.openallay.client.observation.MinecraftHitFacts.worldBorderHit(client, blockHit),
                            properties,
                            fluid.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(fluid.getType()).toString()),
                    null);
        }
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
            var entity = entityHit.getEntity();
            return new WorldFocusObservation.Target(
                    "entity",
                    position,
                    null,
                    new WorldFocusObservation.Entity(
                            entity.getUUID(),
                            entity.getId(),
                            BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                            entity.getName().getString(),
                            position(entity.position()),
                            blockPosition(entity.blockPosition()),
                            entity.isAlive()));
        }
        throw new IllegalStateException("Native hit result payload does not match its kind");
    }

    private static WorldFocusObservation.Screen screen(
            Minecraft client, Screen nativeScreen, boolean overlay) {
        if (nativeScreen == null) {
            return new WorldFocusObservation.Screen(
                    "", "", client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(),
                    false, false, overlay ? "overlay" : "gameplay");
        }
        String className = nativeScreen.getClass().getName();
        String role = overlay ? "overlay"
                : className.startsWith("dev.openallay.client.") ? "openallay" : "game_ui";
        return new WorldFocusObservation.Screen(
                className,
                nativeScreen.getTitle().getString(),
                nativeScreen.width,
                nativeScreen.height,
                nativeScreen.isPauseScreen(),
                MinecraftClientWindow.isInGameUi(client, nativeScreen),
                role);
    }

    private static WorldFocusObservation.Menu menu(
            Minecraft client, Screen nativeScreen, boolean overlay) {
        AbstractContainerMenu nativeMenu = nativeScreen instanceof AbstractContainerScreen<?> container
                ? container.getMenu() : client.player.containerMenu;
        boolean displayed = !overlay && nativeScreen instanceof AbstractContainerScreen<?>;
        String type = "";
        boolean typeAvailable = false;
        String diagnostic = "";
        try {
            type = BuiltInRegistries.MENU.getKey(nativeMenu.getType()).toString();
            typeAvailable = true;
        } catch (UnsupportedOperationException untyped) {
            // Player inventory and creative menus are valid native menus without a MenuType.
            diagnostic = "untyped_menu";
        }
        return new WorldFocusObservation.Menu(
                nativeMenu.getClass().getName(),
                nativeMenu.containerId,
                nativeMenu.getStateId(),
                type,
                typeAvailable,
                displayed,
                nativeMenu == client.player.containerMenu,
                nativeMenu.slots.size(),
                item(client, nativeMenu.getCarried()),
                diagnostic);
    }

    private static WorldFocusObservation.Hover hover(
            Minecraft client, Screen nativeScreen, boolean overlay) {
        double x = dev.openallay.client.context.MinecraftMouseCoordinates.x(client.mouseHandler, client.getWindow());
        double y = dev.openallay.client.context.MinecraftMouseCoordinates.y(client.mouseHandler, client.getWindow());
        boolean mouseGrabbed = client.mouseHandler.isMouseGrabbed();
        if (overlay || mouseGrabbed || !(nativeScreen instanceof AbstractContainerScreen<?>)) {
            return new WorldFocusObservation.Hover(x, y, mouseGrabbed, "none", -1, -1, null, "");
        }
        if (!(nativeScreen instanceof AbstractContainerScreenObservationAccessor accessor)) {
            return new WorldFocusObservation.Hover(
                    x, y, mouseGrabbed, "unavailable", -1, -1, null, "native_hover_invoker_unavailable");
        }
        Slot slot = accessor.openallay$getHoveredSlot(x, y);
        if (slot == null) {
            return new WorldFocusObservation.Hover(x, y, mouseGrabbed, "none", -1, -1, null, "");
        }
        return new WorldFocusObservation.Hover(
                x, y, mouseGrabbed, "slot", slot.index, slot.getContainerSlot(), item(client, slot.getItem()), "");
    }

    private static WorldFocusObservation.Item item(Minecraft client, ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int count = stack.getCount();
        String name = stack.isEmpty() ? "" : stack.getHoverName().getString();
        int damage = stack.getDamageValue();
        int maxDamage = stack.getMaxDamage();
        JsonObject components = new JsonObject();
        boolean available = false;
        String diagnostic;
        try {
            var encoded = dev.openallay.context.minecraft.MinecraftItemDataFacts
                    .persistentData(stack, client.level.registryAccess());
            var result = encoded.result();
            if (result.isEmpty()) {
                // A partial codec result is not a complete persistent component map.
                diagnostic = shortDiagnostic("component_codec_error: "
                        + encoded.error().map(error -> error.message()).orElse("No complete result"));
            } else if (!result.get().isJsonObject()) {
                diagnostic = "component_codec_error: Expected a JSON object";
            } else {
                components = result.get().getAsJsonObject();
                available = true;
                diagnostic = "";
            }
        } catch (RuntimeException failure) {
            diagnostic = shortDiagnostic("component_codec_error: " + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
        }
        return new WorldFocusObservation.Item(id, count, name, damage, maxDamage, components, available, diagnostic);
    }

    private static WorldFocusObservation.Position position(Vec3 position) {
        return new WorldFocusObservation.Position(position.x(), position.y(), position.z());
    }

    private static WorldPosition blockPosition(BlockPos position) {
        return new WorldPosition(position.getX(), position.getY(), position.getZ());
    }

    private static String shortDiagnostic(String message) {
        String compact = message.replace('\n', ' ').replace('\r', ' ');
        return compact.length() <= MAX_DIAGNOSTIC_LENGTH
                ? compact : compact.substring(0, MAX_DIAGNOSTIC_LENGTH - 3) + "...";
    }

    private static void requireOwnerThread(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (!client.isSameThread()) {
            throw new IllegalStateException("Client focus must be captured on the Minecraft client thread");
        }
    }
}
