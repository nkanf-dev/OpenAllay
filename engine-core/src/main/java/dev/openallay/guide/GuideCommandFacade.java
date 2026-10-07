package dev.openallay.guide;

import dev.openallay.FeatureServices;
import dev.openallay.tool.ToolResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Loader-neutral command behavior and notices. */
public final class GuideCommandFacade {
    private final FeatureServices runtime;
    private final GuideServiceManager services;
    private final GuideContextProvider contexts;
    private final GuideScreenOpener screens;

    public GuideCommandFacade(
            FeatureServices runtime,
            GuideServiceManager services,
            GuideContextProvider contexts,
            GuideScreenOpener screens) {
        this.runtime = runtime;
        this.services = services;
        this.contexts = contexts;
        this.screens = screens;
    }

    public void open(UUID actor, Consumer<GuideNotice> notices) {
        emit(screens.open(services.forActor(actor)), notices, ignored -> "已打开 OpenAllay");
    }

    public void ask(UUID actor, String question, Consumer<GuideNotice> notices) {
        GuideService service = services.forActor(actor);
        final GuideSubscription[] subscription = new GuideSubscription[1];
        final UUID[] requestId = new UUID[1];
        Set<String> seenTools = new LinkedHashSet<>();
        final GuideRequestStatus[] seenStatus = {null};
        subscription[0] = service.subscribe(snapshot -> {
            if (requestId[0] == null) return;
            GuideRequestSnapshot request = find(snapshot, requestId[0]);
            if (request == null) return;
            for (GuideToolActivity tool : request.tools()) {
                if (seenTools.add(tool.invocationId())) {
                    GuideToolIntent intent = tool.intent();
                    String actionText = (intent != null && !intent.empty() && !intent.title().isBlank())
                            ? "正在执行：" + intent.title()
                            : "正在执行操作……";
                    notices.accept(GuideNotice.info(actionText));
                }
            }
            if (request.status() == GuideRequestStatus.RATE_LIMITED
                    && seenStatus[0] != GuideRequestStatus.RATE_LIMITED) {
                long seconds = Math.max(1, Math.round(request.retryAfterMillis() / 1000.0));
                notices.accept(GuideNotice.info(
                        "请求过于频繁，约 " + seconds + " 秒后自动重试……"));
            }
            if (request.terminal()) {
                if (request.status() == GuideRequestStatus.COMPLETED) {
                    notices.accept(GuideNotice.info(request.assistantText()));
                } else {
                    notices.accept(GuideNotice.error(
                            request.failure().code() + ": " + request.failure().message()));
                }
                subscription[0].close();
            }
            seenStatus[0] = request.status();
        });
        service.ask(question).thenAccept(result -> {
            final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<java.util.UUID> value; ToolResult.Success<UUID> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<UUID>) $oaPattern0_holder.value) != null))) {
                requestId[0] = $oaPattern0_holder.bound.value();
                notices.accept(GuideNotice.info(
                        "思索中… 会话: " + service.snapshot().selectedSession()));
                service.refreshCapabilities();
            } else {
                subscription[0].close();
                failure((ToolResult.Failure<UUID>) result, notices);
            }
        });
    }

    public void cancel(UUID actor, Consumer<GuideNotice> notices) {
        services.forActor(actor).cancel().thenAccept(result -> emit(
                result,
                notices,
                cancelled -> cancelled ? "已取消" : "当前会话没有运行中的请求"));
    }

    public void retry(UUID actor, Consumer<GuideNotice> notices) {
        GuideService service = services.forActor(actor);
        GuideRequestSnapshot request = service.snapshot().sessions().stream()
                .filter(value -> value.sessionId().equals(service.snapshot().selectedSession()))
                .flatMap(value -> value.requests().stream())
                .filter(value -> value.status() == GuideRequestStatus.FAILED
                        || value.status() == GuideRequestStatus.CANCELLED)
                .reduce((first, second) -> second)
                .orElse(null);
        if (request == null) {
            notices.accept(GuideNotice.error("retry_unavailable: 当前会话没有可重试请求"));
            return;
        }
        service.retry(request.requestId()).thenAccept(result -> emit(
                result, notices, id -> "已重试；新请求: " + id));
    }

    public void clear(UUID actor, Consumer<GuideNotice> notices) {
        services.forActor(actor).clearSelectedSession().thenAccept(result -> emit(
                result, notices, ignored -> "已清除当前会话"));
    }

    public void select(UUID actor, String session, Consumer<GuideNotice> notices) {
        services.forActor(actor).selectSession(session).thenAccept(result -> emit(
                result, notices, id -> "已切换到会话 " + id));
    }

    public void close(UUID actor, String session, Consumer<GuideNotice> notices) {
        services.forActor(actor).closeSession(session).thenAccept(result -> emit(
                result, notices, closed -> closed ? "已关闭会话 " + session : "会话不存在"));
    }

    public void sessions(UUID actor, Consumer<GuideNotice> notices) {
        GuideSnapshot snapshot = services.forActor(actor).snapshot();
        notices.accept(GuideNotice.info("会话 " + snapshot.sessions().stream()
                .map(GuideSessionSnapshot::sessionId).toList()
                + "；当前 " + snapshot.selectedSession()));
    }

    public void model(UUID actor, GuideModelMode mode, Consumer<GuideNotice> notices) {
        services.forActor(actor).setModelMode(mode).thenAccept(result -> emit(
                result, notices, selected -> "模型模式已切换为 " + selected.name().toLowerCase()));
    }

    public void modelProfile(UUID actor, String profileId, Consumer<GuideNotice> notices) {
        GuideService service = services.forActor(actor);
        GuideModelSelection selection;
        try {
            selection = GuideModelSelection.client(profileId);
        } catch (IllegalArgumentException invalid) {
            notices.accept(GuideNotice.error("invalid_model_selection: 模型配置 ID 无效"));
            return;
        }
        service.setModelSelection(selection).thenAccept(result -> emit(
                result,
                notices,
                selected -> "当前会话的模型已切换为 " + service.snapshot().clientProfiles().stream()
                        .filter(profile -> profile.id().equals(selected.profileId()))
                        .map(GuideClientModelProfile::displayName)
                        .findFirst()
                        .orElse(selected.profileId())));
    }

    public void models(UUID actor, Consumer<GuideNotice> notices) {
        GuideSnapshot snapshot = services.forActor(actor).snapshot();
        List<String> choices = new java.util.ArrayList<>();
        snapshot.clientProfiles().stream()
                .filter(GuideClientModelProfile::enabled)
                .map(profile -> profile.displayName() + " (" + profile.id() + ")"
                        + (profile.available() ? "" : " [不可用]"))
                .forEach(choices::add);
        if (snapshot.serverModelAvailable()
                || snapshot.modelSelection().kind() == GuideModelSelection.Kind.SERVER) {
            choices.add("服务端模型"
                    + (snapshot.serverModelAvailable() ? "" : " [不可用]"));
        }
        notices.accept(GuideNotice.info("可选模型: " + choices));
    }

    public void status(UUID actor, Consumer<GuideNotice> notices) {
        GuideSnapshot snapshot = services.forActor(actor).snapshot();
        notices.accept(GuideNotice.info(
                "客户端模型: " + (snapshot.clientModelAvailable() ? "已就绪" : "未配置")
                        + "；当前会话: " + snapshot.selectedSession()
                        + "；已加载文档: " + runtime.knowledge().snapshot().documents().size()
                        + "；服务端模型: " + (snapshot.serverModelAvailable() ? "可用" : "未连接")
                        + "；运行模式: " + snapshot.modelMode().name().toLowerCase()));
    }

    public void skills(Consumer<GuideNotice> notices) {
        notices.accept(GuideNotice.info("Skills: " + runtime.skills().metadata().stream()
                .map(value -> value.name()).toList()));
    }

    public void sources(Consumer<GuideNotice> notices) {
        ToolResult<Integer> refreshed = contexts.refreshKnowledge();
        final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<java.lang.Integer> value; ToolResult.Failure<Integer> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = refreshed) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<Integer>) $oaPattern1_holder.value) != null))) {
            failure($oaPattern1_holder.bound, notices);
            return;
        }
        notices.accept(GuideNotice.info("知识来源: " + runtime.knowledge().snapshot().documents().stream()
                .map(value -> value.sourceId()).distinct().sorted().toList()));
    }

    private static GuideRequestSnapshot find(GuideSnapshot snapshot, UUID requestId) {
        return snapshot.sessions().stream()
                .flatMap(value -> value.requests().stream())
                .filter(value -> value.requestId().equals(requestId))
                .findFirst()
                .orElse(null);
    }

    private static <T> void emit(
            ToolResult<T> result,
            Consumer<GuideNotice> notices,
            java.util.function.Function<T, String> success) {
        final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<T> value; ToolResult.Success<T> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern2_holder.bound = (ToolResult.Success<T>) $oaPattern2_holder.value) != null))) {
            notices.accept(GuideNotice.info(success.apply($oaPattern2_holder.bound.value())));
        } else {
            failure((ToolResult.Failure<T>) result, notices);
        }
    }

    private static void failure(
            ToolResult.Failure<?> failure, Consumer<GuideNotice> notices) {
        notices.accept(GuideNotice.error(failure.code() + ": " + failure.message()));
    }
}
