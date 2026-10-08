package dev.openallay.settings;

import dev.openallay.guide.GuideHistorySettingsSnapshot;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsAggregator;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** One-time late binding that resolves the current Guide actor at action time. */
public final class ClientSettingsHistoryBinding
        implements ClientSettingsService.HistoryActions {
    private final AtomicReference<GuideServiceManager> manager = new AtomicReference<>();

    public void bind(GuideServiceManager replacement) {
        Objects.requireNonNull(replacement, "replacement");
        GuideServiceManager current = manager.get();
        if (current == replacement) {
            return;
        }
        if (!manager.compareAndSet(null, replacement)) {
            throw new IllegalStateException("settings history binding is already configured");
        }
    }

    @Override
    public ClientSettingsService.HistoryRuntimeState state() {
        GuideServiceManager current = manager.get();
        if (current == null) {
            return ClientSettingsService.HistoryRuntimeState.disconnected();
        }
        GuideHistorySettingsSnapshot snapshot = current.historySettingsSnapshot();
        if (dev.openallay.util.Java8ApiSupport.isEmpty(snapshot.guide())) {
            return new ClientSettingsService.HistoryRuntimeState(
                    snapshot.configured(),
                    java.util.Optional.empty(),
                    snapshot.activity(),
                    SettingsDiagnosticsAggregator.HistoryScopeKind.NONE);
        }
        {
final boolean $oaSwitch0_exit_result_prior0 = true;
final java.util.Optional<dev.openallay.guide.GuideSnapshot> $oaSwitch0_exit_result_prior1 = snapshot.guide();
final dev.openallay.guide.history.GuideHistoryActivity $oaSwitch0_exit_result_prior2 = snapshot.activity();
dev.openallay.settings.diagnostics.SettingsDiagnosticsAggregator.HistoryScopeKind $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((snapshot.scopeKind().orElseThrow(() -> new java.util.NoSuchElementException("No value present")))) {
case SINGLEPLAYER:
{
$oaSwitch0_exit_result = SettingsDiagnosticsAggregator.HistoryScopeKind.SINGLEPLAYER_WORLD; break $oaSwitch0_exit;
}
case MULTIPLAYER:
{
$oaSwitch0_exit_result = SettingsDiagnosticsAggregator.HistoryScopeKind.MULTIPLAYER_SERVER; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return new ClientSettingsService.HistoryRuntimeState(
                $oaSwitch0_exit_result_prior0,
                $oaSwitch0_exit_result_prior1,
                $oaSwitch0_exit_result_prior2,
                $oaSwitch0_exit_result,
                snapshot.estimatedContextTokens());
}
    }

    @Override
    public CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory() {
        GuideService service = service();
        return service == null ? unavailable() : service.deleteCurrentHistory();
    }

    @Override
    public CompletableFuture<ToolResult<Boolean>> deleteActorHistory() {
        GuideService service = service();
        return service == null ? unavailable() : service.deleteActorHistory();
    }

    @Override
    public CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase() {
        GuideServiceManager current = manager.get();
        return current == null ? unavailable() : current.resetHistoryDatabase();
    }

    private GuideService service() {
        GuideServiceManager current = manager.get();
        return current == null ? null : current.current();
    }

    private static CompletableFuture<ToolResult<Boolean>> unavailable() {
        return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "history_unavailable", "Durable Guide history is unavailable"));
    }
}
