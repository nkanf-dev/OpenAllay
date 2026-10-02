package dev.openallay.guide;

import com.google.gson.Gson;
import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.guide.history.GuideHistoryAccess;
import dev.openallay.guide.history.GuideHistoryScope;
import dev.openallay.guide.history.GuideHistoryScopeProvider;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Creates exactly one connection-scoped GuideService for the current client actor. */
public final class GuideServiceManager {
    private final GuideLocalEndpoint local;
    private final GuideRemoteEndpoint remote;
    private final GuideContextProvider contexts;
    private final ClientEventDispatcher dispatcher;
    private final Clock clock;
    private final Gson gson;
    private final GuideHistoryAccess history;
    private final GuideHistoryScopeProvider historyScopes;
    private final dev.openallay.model.image.ImageAttachmentStore attachmentStore;
    private GuideService current;
    private final java.util.concurrent.CopyOnWriteArrayList<GuidePresentationListener> presentationListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private GuideSubscription presentationSubscription;

    public GuideServiceManager(
            GuideLocalEndpoint local,
            GuideRemoteEndpoint remote,
            GuideContextProvider contexts,
            ClientEventDispatcher dispatcher,
            Clock clock,
            Gson gson) {
        this(local, remote, contexts, dispatcher, clock, gson, null, null);
    }

    public GuideServiceManager(
            GuideLocalEndpoint local,
            GuideRemoteEndpoint remote,
            GuideContextProvider contexts,
            ClientEventDispatcher dispatcher,
            Clock clock,
            Gson gson,
            GuideHistoryAccess history,
            GuideHistoryScopeProvider historyScopes) {
        this(local, remote, contexts, dispatcher, clock, gson, history, historyScopes, null);
    }

    public GuideServiceManager(
            GuideLocalEndpoint local, GuideRemoteEndpoint remote, GuideContextProvider contexts,
            ClientEventDispatcher dispatcher, Clock clock, Gson gson, GuideHistoryAccess history,
            GuideHistoryScopeProvider historyScopes,
            dev.openallay.model.image.ImageAttachmentStore attachmentStore) {
        this.attachmentStore = attachmentStore;
        this.local = local;
        this.remote = Objects.requireNonNull(remote, "remote");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.gson = Objects.requireNonNull(gson, "gson");
        if ((history == null) != (historyScopes == null)) {
            throw new IllegalArgumentException("history access and scope provider must be configured together");
        }
        this.history = history;
        this.historyScopes = historyScopes;
    }

    public synchronized GuideService forActor(UUID actor) {
        GuideHistoryScope scope = historyScopes == null ? null : historyScopes.resolve(actor);
        if (current == null
                || !current.snapshot().actorId().equals(actor)
                || !Objects.equals(current.historyScope(), scope)) {
            GuideHistoryAccess nextHistory = history;
            if (current != null) {
                invalidatePresentationBinding();
                CompletableFuture<Void> disconnected = current.disconnect();
                if (history != null) {
                    nextHistory = afterDisconnect(disconnected);
                }
            }
            contexts.clearConnectionState();
            current = new GuideService(
                    actor, local, remote, contexts, dispatcher, clock, gson, scope, nextHistory,
                    attachmentStore);
            // Bind synchronously, before this service can be returned for request admission.
            presentationSubscription = current.subscribePresentation(event -> {
                for (GuidePresentationListener listener : presentationListeners) {
                    try { listener.event(event); }
                    catch (RuntimeException ignored) { /* UI observers cannot fail tasks. */ }
                }
            });
            for (GuidePresentationListener listener : presentationListeners) notifyBound(listener, current);
        }
        return current;
    }

    public synchronized CompletableFuture<Void> disconnect() {
        contexts.clearConnectionState();
        if (current != null) {
            invalidatePresentationBinding();
            CompletableFuture<Void> disconnected = current.disconnect();
            current = null;
            return disconnected;
        }
        remote.disconnect();
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> shutdown() {
        return disconnect();
    }

    public synchronized GuideService current() {
        return current;
    }

    /** Register at client initialization, not from HUD tick or snapshot diffs. */
    public synchronized GuideSubscription listenPresentation(GuidePresentationListener listener) {
        Objects.requireNonNull(listener, "listener");
        presentationListeners.add(listener);
        if (current != null) notifyBound(listener, current);
        return () -> presentationListeners.remove(listener);
    }

    private static void notifyBound(GuidePresentationListener listener, GuideService service) {
        try { listener.bound(service); }
        catch (RuntimeException ignored) { /* Presentation is not a task admission dependency. */ }
    }

    private void invalidatePresentationBinding() {
        if (current == null) return;
        UUID generation = current.presentationGeneration();
        current.invalidatePresentation();
        if (presentationSubscription != null) presentationSubscription.close();
        presentationSubscription = null;
        for (GuidePresentationListener listener : presentationListeners) {
            try { listener.invalidated(generation); }
            catch (RuntimeException ignored) { /* One owner cannot block another owner's cleanup. */ }
        }
    }

    public synchronized GuideHistorySettingsSnapshot historySettingsSnapshot() {
        if (history == null || current == null || current.historyScope() == null) {
            return GuideHistorySettingsSnapshot.unavailable(history != null);
        }
        return new GuideHistorySettingsSnapshot(
                true,
                java.util.Optional.of(current.snapshot()),
                history.activity(),
                java.util.Optional.of(current.historyScope().kind()),
                current.contextEstimate().map(GuideContextEstimate::estimatedTokens).orElse(null));
    }

    public synchronized CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase() {
        if (current == null) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "history_unavailable", "Durable guide history is unavailable"));
        }
        return current.resetHistoryDatabase();
    }

    private GuideHistoryAccess afterDisconnect(CompletableFuture<Void> disconnected) {
        return new GuideHistoryAccess() {
            @Override
            public CompletableFuture<java.util.Optional<
                    dev.openallay.guide.history.GuideHistoryMetadata>> metadata(
                    GuideHistoryScope scope) {
                return disconnected.thenCompose(ignored -> history.metadata(scope));
            }

            @Override
            public CompletableFuture<dev.openallay.guide.history.GuideHistoryPage> page(
                    dev.openallay.guide.history.GuideHistoryPageRequest request) {
                return disconnected.thenCompose(ignored -> history.page(request));
            }

            @Override
            public CompletableFuture<dev.openallay.guide.history.GuideHistoryContextSeed> context(
                    dev.openallay.guide.history.GuideHistoryContextRequest request) {
                return disconnected.thenCompose(ignored -> history.context(request));
            }

            @Override
            public CompletableFuture<java.util.List<dev.openallay.model.ModelMessage>> requestContext(
                    GuideHistoryScope scope, UUID requestId) {
                return disconnected.thenCompose(ignored -> history.requestContext(scope, requestId));
            }

            @Override
            public CompletableFuture<Void> commit(
                    dev.openallay.guide.history.GuideHistoryCommit commit) {
                return disconnected.thenCompose(ignored -> history.commit(commit));
            }

            @Override
            public CompletableFuture<Void> delete(
                    dev.openallay.guide.history.GuideHistoryDeleteScope scope) {
                return disconnected.thenCompose(ignored -> history.delete(scope));
            }

            @Override
            public CompletableFuture<Void> resetDatabase() {
                return disconnected.thenCompose(ignored -> history.resetDatabase());
            }

            @Override
            public CompletableFuture<Void> flush() {
                return disconnected.thenCompose(ignored -> history.flush());
            }

            @Override
            public dev.openallay.guide.history.GuideHistoryActivity activity() {
                return disconnected.isDone()
                        ? history.activity()
                        : new dev.openallay.guide.history.GuideHistoryActivity(1, false);
            }
        };
    }
}
