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
        return capture(provenance, owningThread, dev.openallay.util.Java8Collections.listOf());
    }

    public static List<RegistryEntrySnapshot> capture(
            String provenance,
            BooleanSupplier owningThread,
            List<? extends RegistryPropertyContributor> contributors) {
        Objects.requireNonNull(owningThread, "owningThread");
        List<? extends RegistryPropertyContributor> propertyContributors = dev.openallay.util.Java8Collections.listCopyOf(contributors);
        if (!owningThread.getAsBoolean()) {
            throw new IllegalStateException("Game content catalog must be captured on its Minecraft owning thread");
        }
        List<RegistryEntrySnapshot> entries = new ArrayList<>();
        net.minecraftforge.registries.IForgeRegistry<net.minecraft.item.Item> itemRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.ITEM;
        itemRegistry.getValuesCollection().forEach(item -> {
            net.minecraft.util.ResourceLocation id = Objects.requireNonNull(itemRegistry.getKey(item));
            dev.openallay.context.minecraft.MinecraftItemDataFacts.Defaults itemData = MinecraftItemDataFacts.defaults(item);
            entries.add(entry(MinecraftResourceId.from(id.toString()), "item",
                    new net.minecraft.item.ItemStack(item).getDisplayName(), provenance,
                    dev.openallay.util.Java8Collections.listOf(item.getUnlocalizedName()), dev.openallay.util.Java8Collections.setOf(), itemData.componentIds(),
                    properties("item", id.toString(), item, itemData.properties(), propertyContributors)));
        });

        net.minecraftforge.registries.IForgeRegistry<net.minecraft.block.Block> blockRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.BLOCK;
        blockRegistry.getValuesCollection().forEach(block -> {
            net.minecraft.util.ResourceLocation id = Objects.requireNonNull(blockRegistry.getKey(block));
            String stateProperties = block.getBlockState().getProperties().stream()
                    .map(property -> property.getName()).sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            JsonObject data = new JsonObject();
            data.addProperty("state_properties", dev.openallay.util.Java8Strings.isBlank(stateProperties) ? "none" : stateProperties);
            JsonArray states = new JsonArray();
            block.getBlockState().getValidStates().forEach(state -> states.add(object(
                    "metadata", block.getMetaFromState(state), "state", state.toString())));
            data.add("states", states);
            entries.add(entry(MinecraftResourceId.from(id.toString()), "block", block.getLocalizedName(),
                    provenance, dev.openallay.util.Java8Collections.listOf(block.getUnlocalizedName()), dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf(),
                    properties("block", id.toString(), block, dev.openallay.util.Java8Collections.mapOf("minecraft:block", data), propertyContributors)));
        });

        net.minecraftforge.registries.IForgeRegistry<net.minecraft.potion.Potion> effectsRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.MOB_EFFECT;
        effectsRegistry.getValuesCollection().forEach(effect -> {
            net.minecraft.util.ResourceLocation id = Objects.requireNonNull(effectsRegistry.getKey(effect));
            entries.add(entry(MinecraftResourceId.from(id.toString()), "effect", I18n.translateToLocal(effect.getName()),
                    provenance, dev.openallay.util.Java8Collections.listOf(effect.getName()), dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf(),
                    properties("effect", id.toString(), effect, dev.openallay.util.Java8Collections.mapOf("minecraft:mob_effect", object(
                            "bad_effect", effect.isBadEffect(), "beneficial", effect.isBeneficial(),
                            "instantaneous", effect.isInstant(), "color", effect.getLiquidColor())), propertyContributors)));
        });

        net.minecraftforge.registries.IForgeRegistry<net.minecraft.potion.PotionType> potionRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.POTION;
        potionRegistry.getValuesCollection().forEach(potion -> {
            net.minecraft.util.ResourceLocation id = Objects.requireNonNull(potionRegistry.getKey(potion));
            String translationKey = potion.getNamePrefixed("item.potion.effect.");
            JsonArray effects = new JsonArray();
            potion.getEffects().forEach(instance -> {
                net.minecraft.util.ResourceLocation effectId = effectsRegistry.getKey(instance.getPotion());
                if (effectId == null) return;
                effects.add(object("id", effectId.toString(), "duration", instance.getDuration(),
                        "amplifier", instance.getAmplifier(), "ambient", instance.getIsAmbient(),
                        "visible", instance.doesShowParticles()));
            });
            JsonObject data = new JsonObject();
            data.addProperty("name", MinecraftPotionFacts.name(potion));
            data.add("effects", effects);
            entries.add(entry(MinecraftResourceId.from(id.toString()), "potion", I18n.translateToLocal(translationKey),
                    provenance, dev.openallay.util.Java8Collections.listOf(translationKey), dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf(),
                    properties("potion", id.toString(), potion, dev.openallay.util.Java8Collections.mapOf("minecraft:potion", data), propertyContributors)));
        });

        net.minecraftforge.registries.IForgeRegistry<net.minecraftforge.fml.common.registry.EntityEntry> entityRegistry = dev.openallay.platform.minecraft.MinecraftNativeRegistries.ENTITY_TYPE;
        entityRegistry.getValuesCollection().forEach(entity -> {
            net.minecraft.util.ResourceLocation id = Objects.requireNonNull(entityRegistry.getKey(entity));
            String translationKey = "entity." + entity.getName() + ".name";
            entries.add(entry(MinecraftResourceId.from(id.toString()), "entity", I18n.translateToLocal(translationKey),
                    provenance, dev.openallay.util.Java8Collections.listOf(entity.getName(), translationKey), dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf(),
                    properties("entity", id.toString(), entity, dev.openallay.util.Java8Collections.mapOf("minecraft:entity_type", object(
                            "native_class", entity.getEntityClass().getName())), propertyContributors)));
        });
        // No Attribute registry exists in Forge 14. No native registry tags or effect icon flags exist.

        entries.sort(Comparator.comparing(RegistryEntrySnapshot::id)
                .thenComparing(RegistryEntrySnapshot::kind));
        return dev.openallay.util.Java8Collections.listCopyOf(entries);
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
                dev.openallay.util.Java8Strings.isBlank(displayName) ? humanize(id.path()) : displayName,
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
        return dev.openallay.util.Java8Collections.mapCopyOf(result);
    }

    private static JsonObject object(Object... entries) {
        JsonObject result = new JsonObject();
        for (int index = 0; index < entries.length; index += 2) {
            String key = (String) entries[index];
            Object value = entries[index + 1];
            final class $oaPattern0_Holder { java.lang.Object value; Boolean bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = value) instanceof java.lang.Boolean && (($oaPattern0_holder.bound = (Boolean) $oaPattern0_holder.value) != null))) result.addProperty(key, $oaPattern0_holder.bound);
            else {
final class $oaPattern1_Holder { java.lang.Object value; Number bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = value) instanceof java.lang.Number && (($oaPattern1_holder.bound = (Number) $oaPattern1_holder.value) != null))) result.addProperty(key, $oaPattern1_holder.bound);
            else result.addProperty(key, String.valueOf(value));
}
        }
        return result;
    }

    private static String humanize(String path) {
        String value = dev.openallay.util.Java8Strings.strip(path.replace('_', ' ').replace('-', ' ').replace('/', ' '));
        if (value.isEmpty()) {
            return path;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
