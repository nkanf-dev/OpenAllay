package dev.openallay.context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exact canonical context source reporter; original modern and current Java8 owners. */
public final class ContextSnapshotsJava8Fixture {
    private ContextSnapshotsJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        boolean java8 = args.length == 1 && args[0].equals("java8");
        List<Class<?>> owners = Arrays.<Class<?>>asList(ItemStackSnapshot.class, InventorySlotSnapshot.class,
                InventorySnapshot.class, BlockPositionSnapshot.class, PlayerSnapshot.class, CallerSnapshot.class,
                ContextMetrics.class, SourceObservation.class, RecipeLayoutSnapshot.class, RecipeProcessingSnapshot.class);
        if (java8) {
            equal("1.8", System.getProperty("java.specification.version"));
            for (Class<?> owner : owners) classMajor(owner);
        }
        EvidenceMetadata evidence = evidence(Instant.EPOCH, Collections.singletonMap("test:origin", "value"));
        ItemStackSnapshot empty = ItemStackSnapshot.empty();
        ItemStackSnapshot stack = new ItemStackSnapshot("test:item", 3, "Ａ Item");
        InventorySlotSnapshot slot = new InventorySlotSnapshot(0, stack);
        List<InventorySlotSnapshot> inputs = new ArrayList<>(Arrays.asList(slot));
        InventorySnapshot inventory = new InventorySnapshot(inputs, 1, 0, 0, empty, true, evidence);
        inputs.clear(); equal(1, inventory.slots().size());
        try { inventory.slots().clear(); throw new AssertionError("mutable inventory"); } catch (UnsupportedOperationException expected) {}
        BlockPositionSnapshot position = new BlockPositionSnapshot(-7, 64, 9);
        PlayerSnapshot player = new PlayerSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000007"),
                "Player", "minecraft:overworld", position, "creative", inventory, evidence);
        CallerSnapshot caller = new CallerSnapshot(CallerKind.PLAYER, player.uuid(), "Player", true);
        CallerSnapshot console = new CallerSnapshot(CallerKind.CONSOLE, null, "Console", false);
        ContextMetrics metrics = new ContextMetrics(1, 2, 3, 4, 5);
        RecipeLayoutSnapshot layout = new RecipeLayoutSnapshot(3, 2, true);
        RecipeProcessingSnapshot processing = new RecipeProcessingSnapshot(20L, 40L, -0.0d);
        SourceObservation source = new SourceObservation(evidence, Instant.ofEpochSecond(5));
        equal(Instant.EPOCH, source.firstCapturedAt()); equal(evidence.details(), source.identityDetails());
        for (Object value : Arrays.asList(empty, stack, slot, inventory, position, player, caller, console, metrics,
                layout, RecipeLayoutSnapshot.unknown(), processing, RecipeProcessingSnapshot.unknown(), source)) {
            System.out.println(value.toString());
        }
        // Hash oracle is evaluated inside each VM; enum identity hash is not compared across processes.
        int expectedHash = 0;
        for (Object value : new Object[] {player.uuid(), player.displayName(), player.dimension(), player.position(),
                player.gameMode(), player.inventory(), player.evidence()}) expectedHash = 31 * expectedHash + java.util.Objects.hashCode(value);
        equal(expectedHash, player.hashCode());
        equal(player, new PlayerSnapshot(player.uuid(), player.displayName(), player.dimension(), position, player.gameMode(), inventory, evidence));
        check(!position.equals(new BlockPositionSnapshot(-7, 64, 10)), "component inequality");
        check(!processing.equals(new RecipeProcessingSnapshot(20L, 40L, 0.0d)), "boxed double signedzero");
        failure("itemIdentifier", () -> new ItemStackSnapshot("bad", 1, "Item"));
        failure("itemCount", () -> new ItemStackSnapshot("test:item", -1, "Item"));
        failure("itemDisplay", () -> new ItemStackSnapshot("test:item", 1, " "));
        failure("slotNegative", () -> new InventorySlotSnapshot(-1, stack));
        failure("slotStack", () -> new InventorySlotSnapshot(0, null));
        failure("inventoryComplete", () -> new InventorySnapshot(Collections.<InventorySlotSnapshot>emptyList(), 1, 0, 0, empty, true, evidence));
        failure("inventoryDuplicate", () -> new InventorySnapshot(Arrays.asList(slot, slot), 2, 0, 0, empty, true, evidence));
        failure("inventoryRange", () -> new InventorySnapshot(Arrays.asList(new InventorySlotSnapshot(2, stack)), 1, 0, 0, empty, true, evidence));
        failure("inventorySelected", () -> new InventorySnapshot(Arrays.asList(slot), 1, 9, 0, empty, true, evidence));
        failure("inventoryHand", () -> new InventorySnapshot(Arrays.asList(slot), 1, 0, 1, empty, true, evidence));
        failure("inventoryNullSlot", () -> new InventorySnapshot(Arrays.asList((InventorySlotSnapshot)null), 1, 0, 0, empty, true, evidence));
        failure("playerUuid", () -> new PlayerSnapshot(null, "Player", "minecraft:overworld", position, "creative", inventory, evidence));
        failure("playerDimension", () -> new PlayerSnapshot(player.uuid(), "Player", "bad", position, "creative", inventory, evidence));
        failure("callerPlayerUuid", () -> new CallerSnapshot(CallerKind.PLAYER, null, "Player", false));
        failure("callerConsoleUuid", () -> new CallerSnapshot(CallerKind.CONSOLE, player.uuid(), "Console", false));
        failure("metricsNegative", () -> new ContextMetrics(0, 0, 0, 0, -1));
        failure("layoutHalf", () -> new RecipeLayoutSnapshot(0, 2, false));
        failure("layoutShapedUnknown", () -> new RecipeLayoutSnapshot(0, 0, true));
        failure("processingDuration", () -> new RecipeProcessingSnapshot(-1L, null, null));
        failure("processingEnergy", () -> new RecipeProcessingSnapshot(null, -1L, null));
        failure("processingNan", () -> new RecipeProcessingSnapshot(null, null, Double.NaN));
        failure("processingInfinity", () -> new RecipeProcessingSnapshot(null, null, Double.POSITIVE_INFINITY));
        failure("sourceBefore", () -> new SourceObservation(evidence, Instant.ofEpochSecond(-1)));
        Map<String, String> details = new LinkedHashMap<>();
        details.put("openallay_builder:capture_start", Instant.ofEpochSecond(2).toString());
        details.put("openallay_builder:capture_end", Instant.ofEpochSecond(4).toString());
        details.put("test:origin", "value");
        SourceObservation extent = new SourceObservation(evidence(Instant.ofEpochSecond(4), details), Instant.ofEpochSecond(8));
        equal(Instant.ofEpochSecond(2), extent.firstCapturedAt());
        equal(Collections.singletonMap("test:origin", "value"), extent.identityDetails());
        System.out.println("extent=" + extent.firstCapturedAt() + "/" + extent.lastCapturedAt() + "/" + new java.util.TreeMap<>(extent.identityDetails()));
        details.put("openallay_builder:capture_start", "malformed");
        SourceObservation malformed = new SourceObservation(evidence(Instant.ofEpochSecond(4), details));
        equal(Instant.ofEpochSecond(4), malformed.firstCapturedAt()); equal(malformed.evidence().details(), malformed.identityDetails());
        System.out.println("malformed=" + new java.util.TreeMap<>(malformed.identityDetails()));
        if (java8) {
            ValueSchema<InventorySnapshot> inventorySchema = ValueSchemas.of(InventorySnapshot.class);
            check(inventorySchema.components().get(0).genericType() instanceof java.lang.reflect.ParameterizedType, "inventory generic metadata");
            equal(inventory, inventorySchema.construct(new Object[] {inventory.slots(), 1, 0, 0, empty, true, evidence}));
            ValueSchema<SourceObservation> sourceSchema = ValueSchemas.of(SourceObservation.class);
            equal(source, sourceSchema.construct(new Object[] {evidence, Instant.ofEpochSecond(5)}));
            equal((long) owners.size(), owners.stream().filter(ValueSchemas::supports).count());
        }
        JsonObject json = new JsonObject(); json.addProperty("uuid", player.uuid().toString()); json.addProperty("dimension", player.dimension());
        JsonArray coordinates = new JsonArray(); coordinates.add(new JsonPrimitive(position.x())); coordinates.add(new JsonPrimitive(position.y())); coordinates.add(new JsonPrimitive(position.z())); json.add("position", coordinates);
        JsonArray slots = new JsonArray(); for (InventorySlotSnapshot value : inventory.slots()) {
            JsonObject entry = new JsonObject(); entry.addProperty("slot", value.slot()); entry.addProperty("itemId", value.stack().itemId()); entry.addProperty("count", value.stack().count()); slots.add(entry);
        }
        json.add("slots", slots); System.out.println("snapshot=" + json.toString());
        System.out.println("PASS canonical context snapshot vectors");
    }
    private static EvidenceMetadata evidence(Instant capturedAt, Map<String, String> details) {
        return new EvidenceMetadata(DataAuthority.DETERMINISTIC_TEST, DataCompleteness.COMPLETE, capturedAt,
                "test:source", "test:proof", "1.12.2", "forge", details);
    }
    private static void failure(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | NullPointerException expected) {
            // List.copyOf and the Java8 snapshot helper both reject null elements;
            // the JDK's VM-specific null-detail message is not a product error contract.
            String message = name.equals("inventoryNullSlot") ? "null element rejected" : expected.getMessage();
            System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + message);
        }
    }
    private static void classMajor(Class<?> owner) throws Exception {
        try (InputStream input = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            byte[] header = new byte[8]; int position = 0;
            while (position < 8) { int count = input.read(header, position, 8 - position); check(count > 0, "header"); position += count; }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
