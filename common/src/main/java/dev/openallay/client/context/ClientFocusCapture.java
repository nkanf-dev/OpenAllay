package dev.openallay.client.context;

import dev.openallay.client.gui.MinecraftClientWindow;

import com.google.gson.JsonObject;
import dev.openallay.client.context.GuideNativeSlotHitTest;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.platform.PlatformService;
import dev.openallay.world.WorldFocusObservation;
import dev.openallay.world.WorldPosition;
import java.time.Instant;
import java.util.Objects;
import java.util.TreeMap;


import dev.openallay.platform.minecraft.MinecraftNativeRegistries;




/** Reads only public native focus data and a live slot hit-test, without changing client UI state. */
public final class ClientFocusCapture {
    private static final int MAX_DIAGNOSTIC_LENGTH = 240;

    private ClientFocusCapture() {}

    public static WorldFocusObservation capture(net.minecraft.client.Minecraft client, PlatformService platform) {
        requireOwnerThread(client);
        return capture(client, platform, Instant.now());
    }

    /**
     * Sample with the caller's timestamp (for example, the current source frame's sample time).
     * This always reads current native state; it cannot reconstruct an earlier observation.
     */
    public static WorldFocusObservation capture(
            net.minecraft.client.Minecraft client, PlatformService platform, Instant capturedAt) {
        requireOwnerThread(client);
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (!MinecraftClientContextFacts.active(client)) {
            throw new IllegalStateException("No active client player or level");
        }

        net.minecraft.client.player.LocalPlayer player = client.player;
        String dimension = MinecraftClientContextFacts.dimension(client);
        java.lang.Object nativeScreen = MinecraftFocusNativeFacts.screen(client);
        boolean overlay = MinecraftFocusNativeFacts.overlay(client) != null;
        WorldFocusObservation.Menu menu = menu(client, nativeScreen, overlay);
        WorldFocusObservation.Hover hover = hover(client, nativeScreen, overlay);
        TreeMap<String, String> details = new TreeMap<>();
        details.put("minecraft:dimension", dimension);
        details.put("minecraft:camera_source", "minecraft:main_camera");
        details.put("minecraft:target_source", "minecraft:client_hit_result");
        String menuKeyDiagnostic = dev.openallay.client.observation.GuideNativeMenuKeyObservation.diagnostic();
        if (!menuKeyDiagnostic.isEmpty()) details.put("minecraft:menu_key_observation", menuKeyDiagnostic);
        String hitAvailability = dev.openallay.client.observation.MinecraftHitFacts.availability();
        if (!hitAvailability.isEmpty()) details.put("minecraft:hit_availability", hitAvailability);
        details.put("minecraft:component_scope", dev.openallay.context.minecraft.MinecraftItemDataFacts.persistentScope());
        details.put("minecraft:menu_scope", "identity_carried_and_slot_count;slot_contents_not_scanned");
        String uiAvailability = MinecraftFocusNativeFacts.availability();
        if (!uiAvailability.isEmpty()) details.put("minecraft:ui_availability", uiAvailability);
        if (overlay) {
            details.put("minecraft:overlay_class", MinecraftFocusNativeFacts.overlay(client).getClass().getName());
        }
        return new WorldFocusObservation(
                capturedAt,
                MinecraftClientContextFacts.uuid(player),
                dimension,
                camera(client),
                target(client),
                item(client, MinecraftClientContextFacts.mainHand(player)),
                item(client, MinecraftClientContextFacts.offHand(player)),
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
    public static WorldFocusObservation.Camera camera(net.minecraft.client.Minecraft client) {
        requireOwnerThread(client);
        return dev.openallay.client.observation.MinecraftCameraFacts.focus(client);
    }

    private static WorldFocusObservation.Target target(net.minecraft.client.Minecraft client) {
        net.minecraft.world.phys.HitResult hit = dev.openallay.client.observation.MinecraftHitFacts.hit(client);
        if (hit == null) {
            return new WorldFocusObservation.Target("none", null, null, null);
        }
        WorldFocusObservation.Position position = position(dev.openallay.client.observation.MinecraftHitFacts.location(hit));
        // Native MISS is itself a BlockHitResult; test the kind before interpreting block data.
        if (dev.openallay.client.observation.MinecraftHitFacts.kind(hit).equals("miss")) {
            return new WorldFocusObservation.Target("miss", position, null, null);
        }
        if (dev.openallay.client.observation.MinecraftHitFacts.kind(hit).equals("block")) {
            net.minecraft.core.BlockPos blockPos = dev.openallay.client.observation.MinecraftHitFacts.blockPosition(hit);
            net.minecraft.world.level.block.state.BlockState state = dev.openallay.client.observation.MinecraftHitFacts.state(client, blockPos);
            TreeMap<String, String> properties = new TreeMap<>();
            properties.putAll(dev.openallay.context.minecraft.MinecraftBlockStateProperties.capture(state));
            return new WorldFocusObservation.Target(
                    "block",
                    position,
                    new WorldFocusObservation.Block(
                            MinecraftNativeRegistries.BLOCK.getKey(state.getBlock()).toString(),
                            blockPosition(blockPos),
                            dev.openallay.client.observation.MinecraftHitFacts.direction(hit).getName(),
                            dev.openallay.client.observation.MinecraftHitFacts.inside(hit),
                            dev.openallay.client.observation.MinecraftHitFacts.worldBorderHit(client, hit),
                            properties,
                            dev.openallay.client.observation.MinecraftHitFacts.fluid(state)),
                    null);
        }
        if (dev.openallay.client.observation.MinecraftHitFacts.kind(hit).equals("entity")) {
            net.minecraft.world.entity.Entity entity = dev.openallay.client.observation.MinecraftHitFacts.entity(hit);
            return new WorldFocusObservation.Target(
                    "entity",
                    position,
                    null,
                    new WorldFocusObservation.Entity(
                            MinecraftClientContextFacts.uuid(entity),
                            MinecraftClientContextFacts.entityId(entity),
                            dev.openallay.client.observation.MinecraftHitFacts.entityType(entity),
                            MinecraftFocusNativeFacts.entityName(entity),
                            position(MinecraftFocusNativeFacts.entityPosition(entity)),
                            blockPosition(MinecraftClientContextFacts.position(entity)),
                            MinecraftClientContextFacts.alive(entity)));
        }
        throw new IllegalStateException("Native hit result payload does not match its kind");
    }

    private static WorldFocusObservation.Screen screen(
            net.minecraft.client.Minecraft client, Object nativeScreen, boolean overlay) {
        if (nativeScreen == null) {
            return new WorldFocusObservation.Screen(
                    "", "", MinecraftFocusNativeFacts.screenWidth(client, nativeScreen), MinecraftFocusNativeFacts.screenHeight(client, nativeScreen),
                    false, false, overlay ? "overlay" : "gameplay");
        }
        String className = nativeScreen.getClass().getName();
        String role = overlay ? "overlay"
                : className.startsWith("dev.openallay.client.") ? "openallay" : "game_ui";
        return new WorldFocusObservation.Screen(
                className,
                MinecraftFocusNativeFacts.screenTitle(nativeScreen),
                MinecraftFocusNativeFacts.screenWidth(client, nativeScreen),
                MinecraftFocusNativeFacts.screenHeight(client, nativeScreen),
                MinecraftFocusNativeFacts.pauses(nativeScreen),
                MinecraftFocusNativeFacts.inGame(client, nativeScreen),
                role);
    }

    private static WorldFocusObservation.Menu menu(
            net.minecraft.client.Minecraft client, Object nativeScreen, boolean overlay) {
        net.minecraft.world.inventory.AbstractContainerMenu nativeMenu = MinecraftFocusNativeFacts.menu(client, nativeScreen);
        boolean displayed = !overlay && MinecraftFocusNativeFacts.container(nativeScreen);
        String type = "";
        boolean typeAvailable = false;
        String diagnostic = "";
        try {
            type = MinecraftFocusNativeFacts.menuType(nativeMenu);
            typeAvailable = true;
        } catch (UnsupportedOperationException untyped) {
            // Player inventory and creative menus are valid native menus without a MenuType.
            diagnostic = "untyped_menu";
        }
        String stateDiagnostic = MinecraftMenuFacts.stateDiagnostic(nativeMenu);
        if (!stateDiagnostic.isEmpty()) diagnostic = diagnostic.isEmpty() ? stateDiagnostic : diagnostic + ";" + stateDiagnostic;
        return new WorldFocusObservation.Menu(
                nativeMenu.getClass().getName(),
                MinecraftFocusNativeFacts.menuId(nativeMenu),
                MinecraftMenuFacts.stateId(nativeMenu),
                type,
                typeAvailable,
                displayed,
                MinecraftFocusNativeFacts.active(client, nativeMenu),
                MinecraftFocusNativeFacts.slotCount(nativeMenu),
                item(client, MinecraftMenuFacts.carried(client, nativeMenu)),
                diagnostic);
    }

    private static WorldFocusObservation.Hover hover(
            net.minecraft.client.Minecraft client, Object nativeScreen, boolean overlay) {
        double x = dev.openallay.client.context.MinecraftMouseCoordinates.x(client);
        double y = dev.openallay.client.context.MinecraftMouseCoordinates.y(client);
        boolean mouseGrabbed = MinecraftMouseCoordinates.grabbed(client);
        if (overlay || mouseGrabbed || !(MinecraftFocusNativeFacts.container(nativeScreen))) {
            return new WorldFocusObservation.Hover(x, y, mouseGrabbed, "none", -1, -1, null, "");
        }
        final class $oaPattern0_Holder { java.lang.Object value; GuideNativeSlotHitTest bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = nativeScreen) instanceof dev.openallay.client.context.GuideNativeSlotHitTest && (($oaPattern0_holder.bound = (GuideNativeSlotHitTest) $oaPattern0_holder.value) != null)))) {
            return new WorldFocusObservation.Hover(
                    x, y, mouseGrabbed, "unavailable", -1, -1, null, "native_hover_invoker_unavailable");
        }
        net.minecraft.world.inventory.Slot slot = $oaPattern0_holder.bound.openallay$getHoveredSlot(x, y);
        if (slot == null) {
            return new WorldFocusObservation.Hover(x, y, mouseGrabbed, "none", -1, -1, null, "");
        }
        return new WorldFocusObservation.Hover(
                x, y, mouseGrabbed, "slot", MinecraftFocusNativeFacts.slotIndex(slot), MinecraftMenuFacts.containerSlot(slot), item(client, MinecraftFocusNativeFacts.slotItem(slot)), "");
    }

    private static WorldFocusObservation.Item item(net.minecraft.client.Minecraft client, net.minecraft.world.item.ItemStack stack) {
        String id = MinecraftNativeRegistries.ITEM.getKey(stack.getItem()).toString();
        int count = stack.getCount();
        String name = stack.isEmpty() ? "" : MinecraftFocusNativeFacts.itemName(stack);
        int damage = MinecraftFocusNativeFacts.damage(stack);
        int maxDamage = stack.getMaxDamage();
        JsonObject components = new JsonObject();
        boolean available = false;
        String diagnostic;
        try {
            components = MinecraftFocusItemData.persistentData(client, stack);
            available = true;
            diagnostic = "";
        } catch (RuntimeException failure) {
            diagnostic = shortDiagnostic("component_codec_error: " + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
        }
        return new WorldFocusObservation.Item(id, count, name, damage, maxDamage, components, available, diagnostic);
    }

    private static WorldFocusObservation.Position position(net.minecraft.world.phys.Vec3 position) {
        return new WorldFocusObservation.Position(MinecraftFocusNativeFacts.x(position), MinecraftFocusNativeFacts.y(position), MinecraftFocusNativeFacts.z(position));
    }

    private static WorldPosition blockPosition(net.minecraft.core.BlockPos position) {
        return new WorldPosition(position.getX(), position.getY(), position.getZ());
    }

    private static String shortDiagnostic(String message) {
        String compact = message.replace('\n', ' ').replace('\r', ' ');
        return compact.length() <= MAX_DIAGNOSTIC_LENGTH
                ? compact : compact.substring(0, MAX_DIAGNOSTIC_LENGTH - 3) + "...";
    }

    private static void requireOwnerThread(net.minecraft.client.Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (!MinecraftClientContextFacts.ownerThread(client)) {
            throw new IllegalStateException("Client focus must be captured on the Minecraft client thread");
        }
    }
}
