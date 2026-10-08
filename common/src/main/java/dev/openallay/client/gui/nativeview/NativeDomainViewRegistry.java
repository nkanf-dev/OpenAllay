package dev.openallay.client.gui.nativeview;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import dev.openallay.client.gui.MinecraftClientWindow;

/** One Screen's visible native-view lifecycle and provider fallback owner. */
public final class NativeDomainViewRegistry implements AutoCloseable {
    private final Supplier<List<NativeDomainViewProvider>> optionalProviders;
    private final NativeDomainViewProvider fallback;
    private final BooleanSupplier clientThread;
    private final Map<String, Entry> active = new LinkedHashMap<>();
    private final Set<String> visible = new HashSet<>();
    private final Set<NativeDomainViewDiagnostic> diagnostics = new LinkedHashSet<>();

    public NativeDomainViewRegistry() {
        this(
                NativeDomainViewProviderRegistry::providers,
                new GenericRecipeNativeViewProvider(),
                () -> MinecraftClientWindow.ownerThread(MinecraftClientWindow.instance()));
    }

    NativeDomainViewRegistry(
            Supplier<List<NativeDomainViewProvider>> optionalProviders,
            NativeDomainViewProvider fallback,
            BooleanSupplier clientThread) {
        this.optionalProviders = java.util.Objects.requireNonNull(optionalProviders, "optionalProviders");
        this.fallback = java.util.Objects.requireNonNull(fallback, "fallback");
        this.clientThread = java.util.Objects.requireNonNull(clientThread, "clientThread");
    }

    public void beginFrame() {
        requireClientThread();
        visible.clear();
    }

    public NativeDomainView resolve(NativeDomainViewBinding binding) {
        requireClientThread();
        java.util.Objects.requireNonNull(binding, "binding");
        visible.add(binding.stableId());
        Entry retained = active.get(binding.stableId());
        if (retained != null && retained.binding().equals(binding)) {
            return retained.view();
        }
        if (retained != null) {
            close(retained.view());
            active.remove(binding.stableId());
        }
        NativeDomainView created = create(binding);
        active.put(binding.stableId(), new Entry(binding, created));
        return created;
    }

    public boolean render(
            NativeDomainViewBinding binding, NativeDomainView.RenderContext context) {
        NativeDomainView view = resolve(binding);
        try {
            view.render(context);
            return true;
        } catch (LinkageError | RuntimeException failure) {
            diagnostics.add(new NativeDomainViewDiagnostic(
                    binding.stableId(), view.providerId(), "native_view_failed"));
            close(view);
            NativeDomainViewProvider.Attempt attempt = fallback.create(binding);
            final class $oaPattern0_Holder { dev.openallay.client.gui.nativeview.NativeDomainViewProvider.Attempt value; NativeDomainViewProvider.Attempt.Ready bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = attempt) instanceof dev.openallay.client.gui.nativeview.NativeDomainViewProvider.Attempt.Ready && (($oaPattern0_holder.bound = (NativeDomainViewProvider.Attempt.Ready) $oaPattern0_holder.value) != null)))) {
                active.remove(binding.stableId());
                return false;
            }
            active.put(binding.stableId(), new Entry(binding, $oaPattern0_holder.bound.view()));
            try {
                $oaPattern0_holder.bound.view().render(context);
                return true;
            } catch (RuntimeException fallbackFailure) {
                close($oaPattern0_holder.bound.view());
                active.remove(binding.stableId());
                return false;
            }
        }
    }

    public void endFrame() {
        requireClientThread();
        List<String> released = dev.openallay.util.Java8Collections.toList(active.keySet().stream()
                .filter(id -> !visible.contains(id)));
        released.forEach(id -> close(active.remove(id).view()));
    }

    public void tick() {
        requireClientThread();
        dev.openallay.util.Java8Collections.listCopyOf(active.values()).forEach(entry -> {
            try {
                entry.view().tick();
            } catch (RuntimeException failure) {
                diagnostics.add(new NativeDomainViewDiagnostic(
                        entry.binding().stableId(), entry.view().providerId(), "native_view_failed"));
                close(entry.view());
                active.remove(entry.binding().stableId());
            }
        });
    }

    public List<NativeDomainViewDiagnostic> diagnostics() {
        return dev.openallay.util.Java8Collections.listCopyOf(diagnostics);
    }

    public int activeViewCount() {
        return active.size();
    }

    public void clear() {
        requireClientThread();
        active.values().forEach(entry -> close(entry.view()));
        active.clear();
        visible.clear();
    }

    @Override
    public void close() {
        clear();
    }

    private NativeDomainView create(NativeDomainViewBinding binding) {
        List<NativeDomainViewProvider> providers = new java.util.ArrayList<>(optionalProviders.get());
        providers.add(fallback);
        for (NativeDomainViewProvider provider : providers) {
            if (!provider.supports(binding)) continue;
            try {
                NativeDomainViewProvider.Attempt attempt = provider.create(binding);
                final class $oaPattern1_Holder { dev.openallay.client.gui.nativeview.NativeDomainViewProvider.Attempt value; NativeDomainViewProvider.Attempt.Ready bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = attempt) instanceof dev.openallay.client.gui.nativeview.NativeDomainViewProvider.Attempt.Ready && (($oaPattern1_holder.bound = (NativeDomainViewProvider.Attempt.Ready) $oaPattern1_holder.value) != null))) {
                    if ($oaPattern1_holder.bound.view().family() != binding.family()) {
                        close($oaPattern1_holder.bound.view());
                        diagnostics.add(new NativeDomainViewDiagnostic(
                                binding.stableId(), provider.providerId(), "native_view_failed"));
                        continue;
                    }
                    return $oaPattern1_holder.bound.view();
                }
                NativeDomainViewProvider.Attempt.Unsupported unsupported =
                        (NativeDomainViewProvider.Attempt.Unsupported) attempt;
                diagnostics.add(new NativeDomainViewDiagnostic(
                        binding.stableId(), provider.providerId(), unsupported.code()));
            } catch (LinkageError | RuntimeException failure) {
                diagnostics.add(new NativeDomainViewDiagnostic(
                        binding.stableId(), provider.providerId(), "native_view_failed"));
            }
        }
        throw new IllegalStateException("native view fallback did not resolve " + binding.family());
    }

    private void requireClientThread() {
        if (!clientThread.getAsBoolean()) {
            throw new IllegalStateException("native domain views require the Minecraft client thread");
        }
    }

    private static void close(NativeDomainView view) {
        try {
            view.close();
        } catch (RuntimeException ignored) {
            // The view is already detached from the registry; one provider cannot retain it.
        }
    }

    @dev.openallay.value.ValueType(Entry.ValueSchemaProvider.class)
private static final class Entry {
    private final NativeDomainViewBinding binding;
    private final NativeDomainView view;
    private Entry(NativeDomainViewBinding binding, NativeDomainView view) {
        this.binding = binding;
        this.view = view;
    }
    public NativeDomainViewBinding binding() { return binding; }
    public NativeDomainView view() { return view; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Entry)) return false;
        Entry that = (Entry) other;
        return java.util.Objects.equals(binding, that.binding) && java.util.Objects.equals(view, that.view);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(binding);
        hash = 31 * hash + java.util.Objects.hashCode(view);
        return hash;
    }
    @Override public String toString() { return "Entry[binding=" + binding + ", view=" + view + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Entry> schema() {
            return new dev.openallay.value.ValueSchema<>(Entry.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Entry>>asList(new dev.openallay.value.ValueSchema.Component<>(Entry.class, "binding", Entry::binding), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "view", Entry::view)), arguments -> new Entry((NativeDomainViewBinding) arguments[0], (NativeDomainView) arguments[1]));
        }
    }
}
}
