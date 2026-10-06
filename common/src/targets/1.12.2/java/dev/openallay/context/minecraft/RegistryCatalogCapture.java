package dev.openallay.context.minecraft;

import dev.openallay.platform.minecraft.MinecraftResourceId;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.context.RegistryEntrySnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import net.minecraft.util.text.translation.I18n;

/** Captures public built-in catalog data after the caller has proved Minecraft-thread ownership. */
public final class RegistryCatalogCapture {
    private RegistryCatalogCapture() {}

    public static List<RegistryEntrySnapshot> capture(
            String provenance, BooleanSupplier owningThread) {
        return capture(provenance, owningThread, List.of());
    }

    public static List<RegistryEntrySnapshot> capture(
            String provenance,
            BooleanSupplier owningThread,
            List<? extends RegistryPropertyContributor> contributors) {
        Objects.requireNonNull(owningThread, "owningThread");
        List<? extends RegistryPropertyContributor> propertyContributors = List.copyOf(contributors);
        if (!owningThread.getAsBoolean()) {
            throw new IllegalStateException("Game content catalog must be captured on its Minecraft owning thread");
        }
        List<RegistryEntrySnapshot> entries = new ArrayList<>();
        var itemRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.ITEM;
        itemRegistry.getValuesCollection().forEach(item -> {
            var id = Objects.requireNonNull(itemRegistry.getKey(item));
            var itemData = MinecraftItemDataFacts.defaults(item);
            entries.add(entry(MinecraftResourceId.from(id.toString()), "item",
                    new net.minecraft.item.ItemStack(item).getDisplayName(), provenance,
                    List.of(item.getUnlocalizedName()), Set.of(), itemData.componentIds(),
                    properties("item", id.toString(), item, itemData.properties(), propertyContributors)));
        });

        var blockRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.BLOCK;
        blockRegistry.getValuesCollection().forEach(block -> {
            var id = Objects.requireNonNull(blockRegistry.getKey(block));
            String stateProperties = block.getBlockState().getProperties().stream()
                    .map(property -> property.getName()).sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            JsonObject data = new JsonObject();
            data.addProperty("state_properties", stateProperties.isBlank() ? "none" : stateProperties);
            JsonArray states = new JsonArray();
            block.getBlockState().getValidStates().forEach(state -> states.add(object(
                    "metadata", block.getMetaFromState(state), "state", state.toString())));
            data.add("states", states);
            entries.add(entry(MinecraftResourceId.from(id.toString()), "block", block.getLocalizedName(),
                    provenance, List.of(block.getUnlocalizedName()), Set.of(), Set.of(),
                    properties("block", id.toString(), block, Map.of("minecraft:block", data), propertyContributors)));
        });

        var effectsRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.MOB_EFFECT;
        effectsRegistry.getValuesCollection().forEach(effect -> {
            var id = Objects.requireNonNull(effectsRegistry.getKey(effect));
            entries.add(entry(MinecraftResourceId.from(id.toString()), "effect", I18n.translateToLocal(effect.getName()),
                    provenance, List.of(effect.getName()), Set.of(), Set.of(),
                    properties("effect", id.toString(), effect, Map.of("minecraft:mob_effect", object(
                            "bad_effect", effect.isBadEffect(), "beneficial", effect.isBeneficial(),
                            "instantaneous", effect.isInstant(), "color", effect.getLiquidColor())), propertyContributors)));
        });

        var potionRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.POTION;
        potionRegistry.getValuesCollection().forEach(potion -> {
            var id = Objects.requireNonNull(potionRegistry.getKey(potion));
            String translationKey = potion.getNamePrefixed("item.potion.effect.");
            JsonArray effects = new JsonArray();
            potion.getEffects().forEach(instance -> {
                var effectId = effectsRegistry.getKey(instance.getPotion());
                if (effectId == null) return;
                effects.add(object("id", effectId.toString(), "duration", instance.getDuration(),
                        "amplifier", instance.getAmplifier(), "ambient", instance.getIsAmbient(),
                        "visible", instance.doesShowParticles()));
            });
            JsonObject data = new JsonObject();
            data.addProperty("name", MinecraftPotionFacts.name(potion));
            data.add("effects", effects);
            entries.add(entry(MinecraftResourceId.from(id.toString()), "potion", I18n.translateToLocal(translationKey),
                    provenance, List.of(translationKey), Set.of(), Set.of(),
                    properties("potion", id.toString(), potion, Map.of("minecraft:potion", data), propertyContributors)));
        });

        var entityRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.ENTITY_TYPE;
        entityRegistry.getValuesCollection().forEach(entity -> {
            var id = Objects.requireNonNull(entityRegistry.getKey(entity));
            String translationKey = "entity." + entity.getName() + ".name";
            entries.add(entry(MinecraftResourceId.from(id.toString()), "entity", I18n.translateToLocal(translationKey),
                    provenance, List.of(entity.getName(), translationKey), Set.of(), Set.of(),
                    properties("entity", id.toString(), entity, Map.of("minecraft:entity_type", object(
                            "native_class", entity.getEntityClass().getName())), propertyContributors)));
        });
        // No Attribute registry exists in Forge 14. No native registry tags or effect icon flags exist.

        entries.sort(Comparator.comparing(RegistryEntrySnapshot::id)
                .thenComparing(RegistryEntrySnapshot::kind));
        return List.copyOf(entries);
    }

    private static RegistryEntrySnapshot entry(
            MinecraftResourceId id,
            String kind,
            String displayName,
            String provenance,
            List<String> aliases,
            Set<String> tags,
            Set<String> components,
            Map<String, JsonElement> properties) {
        return new RegistryEntrySnapshot(
                id.toString(),
                kind,
                displayName.isBlank() ? humanize(id.path()) : displayName,
                id.namespace(),
                provenance,
                aliases,
                tags,
                components,
                properties);
    }

    private static Map<String, JsonElement> properties(
            String kind,
            String resourceId,
            Object registryValue,
            Map<String, JsonElement> builtIn,
            List<? extends RegistryPropertyContributor> contributors) {
        Map<String, JsonElement> result = new TreeMap<>();
        builtIn.forEach((key, value) -> result.put(key, dev.openallay.json.JsonTrees.copy(value)));
        for (RegistryPropertyContributor contributor : contributors) {
            Objects.requireNonNull(contributor, "registry property contributor");
            Map<String, JsonElement> contributed;
            try {
                contributed = contributor.capture(kind, resourceId, registryValue);
            } catch (RuntimeException unavailable) {
                // One optional Extension must not make the built-in catalog unavailable.
                continue;
            }
            if (contributed == null) continue;
            contributed.forEach((key, value) -> {
                if (key != null && value != null) result.putIfAbsent(key, dev.openallay.json.JsonTrees.copy(value));
            });
        }
        return Map.copyOf(result);
    }

    private static JsonObject object(Object... entries) {
        JsonObject result = new JsonObject();
        for (int index = 0; index < entries.length; index += 2) {
            String key = (String) entries[index];
            Object value = entries[index + 1];
            if (value instanceof Boolean bool) result.addProperty(key, bool);
            else if (value instanceof Number number) result.addProperty(key, number);
            else result.addProperty(key, String.valueOf(value));
        }
        return result;
    }

    private static String humanize(String path) {
        String value = path.replace('_', ' ').replace('-', ' ').replace('/', ' ').strip();
        if (value.isEmpty()) {
            return path;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
