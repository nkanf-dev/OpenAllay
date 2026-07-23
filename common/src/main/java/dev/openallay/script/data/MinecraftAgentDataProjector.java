package dev.openallay.script.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.RegistryEntrySnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.knowledge.KnowledgeSnapshot;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Projects only detached request records into the JavaScript data graph. */
public final class MinecraftAgentDataProjector {
    private final Gson gson;
    private final Supplier<KnowledgeSnapshot> knowledge;
    private final JavascriptDataModuleRegistry extensions;

    public MinecraftAgentDataProjector(Gson gson) {
        this(gson, KnowledgeSnapshot::empty, new JavascriptDataModuleRegistry());
    }

    public MinecraftAgentDataProjector(
            Gson gson, Supplier<KnowledgeSnapshot> knowledge) {
        this(gson, knowledge, new JavascriptDataModuleRegistry());
    }

    public MinecraftAgentDataProjector(
            Gson gson,
            Supplier<KnowledgeSnapshot> knowledge,
            JavascriptDataModuleRegistry extensions) {
        this.gson = Objects.requireNonNull(gson, "gson");
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
    }

    public Projection project(ToolInvocationContext context) {
        Objects.requireNonNull(context, "context");
        JsonObject root = new JsonObject();
        JsonArray capabilities = new JsonArray();
        List<EvidenceMetadata> evidence = new ArrayList<>();

        root.add("caller", gson.toJsonTree(context.caller()));
        root.add("metrics", gson.toJsonTree(context.metrics()));
        root.addProperty("capturedAt", context.capturedAt().toString());

        context.player().ifPresent(player -> {
            root.add("player", gson.toJsonTree(player));
            capabilities.add("player");
            evidence.add(player.evidence());
        });
        context.registries().ifPresent(registries -> {
            JsonObject registryRoot = gson.toJsonTree(registries).getAsJsonObject();
            // KubeJS-style host views expose each collection once. Registry metadata remains
            // available without duplicating every entry beside mc.items/mc.blocks/etc.
            registryRoot.remove("entries");
            root.add("registries", registryRoot);
            capabilities.add("registries");
            evidence.add(registries.evidence());
            addRegistryViews(root, registries.entries());
        });
        context.recipes().ifPresent(recipes -> {
            JsonObject recipeCatalog = gson.toJsonTree(recipes).getAsJsonObject();
            recipeCatalog.remove("recipes");
            root.add("recipeCatalog", recipeCatalog);
            root.add("recipes", gson.toJsonTree(recipes.recipes()));
            capabilities.add("recipes");
            evidence.add(recipes.evidence());
        });
        context.observableGameState().ifPresent(game -> {
            root.add("game", gson.toJsonTree(game));
            capabilities.add("game");
            evidence.add(game.runtime().evidence());
            evidence.add(game.mods().evidence());
            evidence.add(game.options().evidence());
            evidence.add(game.packs().evidence());
            evidence.add(game.shaders().evidence());
            evidence.add(game.diagnostics().evidence());
            evidence.add(game.player().evidence());
            evidence.add(game.worldQueries().evidence());
        });
        KnowledgeSnapshot knowledgeSnapshot = Objects.requireNonNull(
                knowledge.get(), "knowledge snapshot");
        root.add("knowledge", gson.toJsonTree(knowledgeSnapshot.documents()));
        capabilities.add("knowledge");
        evidence.addAll(knowledgeSnapshot.evidence());

        var extensionSnapshot = extensions.capture(context);
        root.add("extensions", extensionSnapshot.values());
        root.add("extensionDiagnostics", extensionSnapshot.diagnostics());
        evidence.addAll(extensionSnapshot.evidence());
        root.add("capabilities", capabilities);
        List<EvidenceMetadata> distinctEvidence = evidence.stream().distinct().toList();
        root.add("evidence", gson.toJsonTree(distinctEvidence));
        return new Projection(root, distinctEvidence);
    }

    private void addRegistryViews(JsonObject root, List<RegistryEntrySnapshot> entries) {
        java.util.Map<String, JsonArray> views = new java.util.TreeMap<>();
        for (RegistryEntrySnapshot entry : entries) {
            String name = pluralize(entry.kind().toLowerCase(Locale.ROOT));
            views.computeIfAbsent(name, ignored -> new JsonArray())
                    .add(gson.toJsonTree(entry));
        }
        views.forEach(root::add);
    }

    private static String pluralize(String kind) {
        return switch (kind) {
            case "item" -> "items";
            case "block" -> "blocks";
            case "fluid" -> "fluids";
            case "effect", "mob_effect" -> "effects";
            case "enchantment" -> "enchantments";
            case "entity", "entity_type" -> "entities";
            default -> kind.endsWith("s") ? kind : kind + "s";
        };
    }

    public record Projection(JsonObject data, List<EvidenceMetadata> evidence) {
        public Projection {
            data = Objects.requireNonNull(data, "data").deepCopy();
            evidence = List.copyOf(evidence);
        }
    }
}
