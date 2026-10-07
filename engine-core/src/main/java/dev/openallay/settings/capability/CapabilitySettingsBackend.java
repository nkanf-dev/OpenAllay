package dev.openallay.settings.capability;

import dev.openallay.FeatureServices;
import dev.openallay.capability.CapabilityCatalogState;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.capability.CapabilityPolicyStore;
import dev.openallay.capability.ClientCapabilityResolver;
import dev.openallay.capability.ClientCapabilitySnapshot;
import dev.openallay.client.ClientModelRuntimeRegistry;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Resolves dependency closure before atomic policy persistence and runtime publication. */
public final class CapabilitySettingsBackend implements ClientSettingsService.CapabilityActions {
    private final FeatureServices product;
    private final CapabilityPolicyStore store;
    private final ClientCapabilityResolver resolver = new ClientCapabilityResolver();
    private final Consumer<ClientCapabilitySnapshot> publish;
    private volatile ClientCapabilitySnapshot current;

    public CapabilitySettingsBackend(
            Path path,
            FeatureServices product,
            ClientModelRuntimeRegistry models) {
        this(path, product, models.capabilities(), models::replaceCapabilities);
    }

    CapabilitySettingsBackend(
            Path path,
            FeatureServices product,
            ClientCapabilitySnapshot initial,
            Consumer<ClientCapabilitySnapshot> publish) {
        this.product = Objects.requireNonNull(product, "product");
        this.store = new CapabilityPolicyStore(path);
        this.current = Objects.requireNonNull(initial, "initial");
        this.publish = Objects.requireNonNull(publish, "publish");
    }

    public CapabilitySettingsView currentView() {
        return view(current.policy());
    }

    @Override
    public ToolResult<CapabilitySettingsView> saveCapabilities(CapabilityPolicy candidate) {
        ToolResult<ClientCapabilitySnapshot> resolved = resolve(candidate);
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.capability.ClientCapabilitySnapshot> value; ToolResult.Failure<ClientCapabilitySnapshot> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = resolved) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<ClientCapabilitySnapshot>) $oaPattern0_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
        }
        ClientCapabilitySnapshot prepared =
                ((ToolResult.Success<ClientCapabilitySnapshot>) resolved).value();
        ToolResult<CapabilityPolicy> saved = store.save(candidate);
        final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.capability.CapabilityPolicy> value; ToolResult.Failure<CapabilityPolicy> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = saved) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<CapabilityPolicy>) $oaPattern1_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message());
        }
        publish.accept(prepared);
        current = prepared;
        return new ToolResult.Success<>(view(candidate));
    }

    /**
     * Publishes a policy reconstructed from Tool-family files without writing the legacy generic
     * capability document. Tool settings remain the durable source of truth.
     */
    public ToolResult<CapabilitySettingsView> publishCapabilities(CapabilityPolicy candidate) {
        CapabilityPolicy normalized = toolOwnedPolicy(candidate);
        ToolResult<ClientCapabilitySnapshot> resolved = resolve(normalized);
        final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<dev.openallay.capability.ClientCapabilitySnapshot> value; ToolResult.Failure<ClientCapabilitySnapshot> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = resolved) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern2_holder.bound = (ToolResult.Failure<ClientCapabilitySnapshot>) $oaPattern2_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern2_holder.bound.code(), $oaPattern2_holder.bound.message());
        }
        ClientCapabilitySnapshot prepared =
                ((ToolResult.Success<ClientCapabilitySnapshot>) resolved).value();
        publish.accept(prepared);
        current = prepared;
        return new ToolResult.Success<>(view(normalized));
    }

    /** Re-captures runtime-controlled availability without changing any explicit deny choices. */
    public ToolResult<CapabilitySettingsView> refreshCapabilities() {
        CapabilityPolicy policy = current.policy();
        ToolResult<ClientCapabilitySnapshot> resolved = resolve(policy);
        final class $oaPattern3_Holder { dev.openallay.tool.ToolResult<dev.openallay.capability.ClientCapabilitySnapshot> value; ToolResult.Failure<ClientCapabilitySnapshot> bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = resolved) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern3_holder.bound = (ToolResult.Failure<ClientCapabilitySnapshot>) $oaPattern3_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern3_holder.bound.code(), $oaPattern3_holder.bound.message());
        }
        ClientCapabilitySnapshot prepared =
                ((ToolResult.Success<ClientCapabilitySnapshot>) resolved).value();
        publish.accept(prepared);
        current = prepared;
        return new ToolResult.Success<>(view(policy));
    }

    private CapabilityPolicy toolOwnedPolicy(CapabilityPolicy candidate) {
        Set<String> knownSkills = product.skills().metadata().stream()
                .map(metadata -> metadata.name())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> disabledSkills = new TreeSet<>(candidate.disabledSkills());
        disabledSkills.removeAll(knownSkills);
        product.skills().metadata().stream()
                .filter(metadata -> metadata.allowedTools().stream()
                        .anyMatch(candidate.disabledTools()::contains))
                .map(metadata -> metadata.name())
                .forEach(disabledSkills::add);
        return new CapabilityPolicy(
                candidate.disabledTools(),
                disabledSkills);
    }

    @Override
    public ToolResult<CapabilitySettingsView> reloadCapabilities() {
        ToolResult<CapabilityPolicy> loaded = store.load();
        final class $oaPattern4_Holder { dev.openallay.tool.ToolResult<dev.openallay.capability.CapabilityPolicy> value; ToolResult.Failure<CapabilityPolicy> bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern4_holder.bound = (ToolResult.Failure<CapabilityPolicy>) $oaPattern4_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern4_holder.bound.code(), $oaPattern4_holder.bound.message());
        }
        CapabilityPolicy policy = ((ToolResult.Success<CapabilityPolicy>) loaded).value();
        ToolResult<ClientCapabilitySnapshot> resolved = resolve(policy);
        final class $oaPattern5_Holder { dev.openallay.tool.ToolResult<dev.openallay.capability.ClientCapabilitySnapshot> value; ToolResult.Failure<ClientCapabilitySnapshot> bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = resolved) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern5_holder.bound = (ToolResult.Failure<ClientCapabilitySnapshot>) $oaPattern5_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern5_holder.bound.code(), $oaPattern5_holder.bound.message());
        }
        ClientCapabilitySnapshot prepared =
                ((ToolResult.Success<ClientCapabilitySnapshot>) resolved).value();
        publish.accept(prepared);
        current = prepared;
        return new ToolResult.Success<>(view(policy));
    }

    private ToolResult<ClientCapabilitySnapshot> resolve(CapabilityPolicy policy) {
        return resolver.resolve(policy, product.tools().registrations(), product.skills());
    }

    private CapabilitySettingsView view(CapabilityPolicy policy) {
        Set<String> knownTools = product.tools().descriptors().stream()
                .map(descriptor -> descriptor.id())
                .filter(id -> !id.equals(ClientCapabilityResolver.LOAD_SKILL_ID))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> knownSkills = product.skills().metadata().stream()
                .map(metadata -> metadata.name())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> unknownTools = new TreeSet<>(policy.disabledTools());
        unknownTools.removeAll(knownTools);
        Set<String> unknownSkills = new TreeSet<>(policy.disabledSkills());
        unknownSkills.removeAll(knownSkills);
        Set<String> disabled = new HashSet<>(policy.disabledTools());
        disabled.addAll(policy.disabledSkills());
        Set<String> unavailable = product.platform().isModLoaded("ftbquests")
                ? dev.openallay.util.Java8Collections.setOf()
                : dev.openallay.util.Java8Collections.setOf("ftbquests");
        return new CapabilitySettingsView(
                policy,
                product.capabilitySettings().snapshot(
                        new CapabilityCatalogState(unavailable, disabled)),
                unknownTools,
                unknownSkills);
    }
}
