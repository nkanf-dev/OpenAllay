package dev.openallay.guide.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.client.MinecraftGuideContextProvider;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.command.MinecraftCommandCapture;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Separate development-only warmup; never injects feedback or executes server commands. */
final class GuideNativeCommandE2EProbe {
    private GuideNativeCommandE2EProbe() {}

    static CancellationSignal start(OpenAllayRuntime runtime,
            MinecraftGuideContextProvider contexts, UUID actor, Consumer<JsonObject> complete) {
        Minecraft client = Minecraft.getInstance();
        CancellationSignal cancellation = new CancellationSignal();
        String token = "openallay_native_command_" + UUID.randomUUID().toString().replace("-", "");
        String correlation = "e2e-native-command-" + token;
        JsonObject receipt = new JsonObject();
        receipt.addProperty("outcome", "WAITING");
        receipt.addProperty("actorId", actor.toString());
        receipt.addProperty("token", token);
        receipt.addProperty("route", "registered-run-javascript/commands.run/MinecraftCommandCapture/native-player-command");
        receipt.addProperty("feedbackOwner", "Forge-ClientChatReceivedEvent-System-and-Player");
        receipt.addProperty("worldAuthorityChanged", false);
        RunJavascriptTool tool;
        ToolInvocationContext context;
        try {
            if (!Boolean.getBoolean(GuideClientE2EConfig.ENABLED) || !client.isSameThread()
                    || client.player == null || !actor.equals(client.player.getUUID()))
                throw new IllegalStateException("Native command warmup requires the development client owner and actor");
            var registered = runtime.tools().find(RunJavascriptTool.ID).orElseThrow();
            if (!(registered instanceof RunJavascriptTool javascript))
                throw new IllegalStateException("Actual registered JavaScript Tool is unavailable");
            tool = javascript;
            contexts.freezeRequest(correlation, true);
            var captured = contexts.capture(tool.descriptor().requiredContext(), correlation);
            if (!(captured instanceof ToolResult.Success<ToolInvocationContext> success))
                throw new IllegalStateException("Actual player context capture failed: " + captured);
            context = success.value();
            if (!context.unrestrictedJavascript() || !runtime.commands().availableFor(correlation)
                    || !actor.equals(context.player().orElseThrow().uuid()))
                throw new IllegalStateException("Native command warmup requires captured client-local full access");
            receipt.addProperty("unrestrictedCaptured", true);
            receipt.addProperty("commandOnlySetting", runtime.commands().enabled());
            receipt.addProperty("clientCaptureOwnerThread", client.isSameThread());
        } catch (RuntimeException failure) {
            cancellation.cancel();
            contexts.closeRequest(correlation);
            receipt.addProperty("outcome", "FAILED");
            receipt.addProperty("failure", failure.toString());
            complete.accept(receipt);
            return cancellation;
        }
        var server = client.getSingleplayerServer();
        if (server == null) {
            cancellation.cancel();
            contexts.closeRequest(correlation);
            tool.closeRequestScope(correlation);
            receipt.addProperty("outcome", "FAILED");
            receipt.addProperty("failure", "Integrated server disappeared before native command warmup");
            complete.accept(receipt);
            return cancellation;
        }
        cancellation.onCancel(() -> {
            try { contexts.closeRequest(correlation); }
            finally { tool.closeRequestScope(correlation); }
        });
        String source = """
                var catalog = commands.list();
                var helpNode = commands.describe('help');
                var messageNode = commands.describe('me <action>');
                var help = commands.run('/help me');
                var signed = commands.run('/me %s');
                var error = commands.run('/help %s_missing');
                return JSON.stringify({helpNode: helpNode.path, messageNode: messageNode.path,
                  help: help, signed: signed, error: error});
                """.formatted(token, token);
        // The real Tool owns its bounded command waits and its existing daemon worker.
        // Neither the client nor server owner thread waits on this future.
        tool.invokeAsync(context, new RunJavascriptTool.Input(source, List.of()), cancellation)
                .orTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .thenCompose(result -> {
                    if (!(result instanceof ToolResult.Success<RunJavascriptTool.Output> success))
                        throw new IllegalStateException("Actual command Tool failed: " + result);
                    var output = success.value();
                    if (!output.complete() || !"string".equals(output.resultType())
                            || output.preview() == null || !output.preview().isJsonPrimitive())
                        throw new IllegalStateException("Command warmup needs a complete scalar receipt");
                    var commands = JsonParser.parseString(output.preview().getAsString()).getAsJsonObject();
                    receipt.add("commands", commands);
                    receipt.addProperty("nativeErrorOracle", "invalid-help-path/nonempty-feedback/differs-from-help-success");
                    requireResults(commands, actor, token);
                    receipt.addProperty("nativeFeedbackEvidence", output.sources().stream().anyMatch(value ->
                            "minecraft:command_feedback".equals(value.evidence().sourceId())));
                    if (!receipt.get("nativeFeedbackEvidence").getAsBoolean())
                        throw new IllegalStateException("Actual command feedback evidence is missing");
                    try {
                        MinecraftCommandCapture.capture(client, runtime.commands(), correlation, Instant.now());
                        throw new IllegalStateException("Off-owner command capture was admitted");
                    } catch (IllegalStateException denied) {
                        if (!"Command capability must be captured on the client thread".equals(denied.getMessage()))
                            throw denied;
                        receipt.addProperty("offOwnerCaptureRejected", true);
                    }
                    cancellation.cancel();
                    receipt.addProperty("closedCapabilityRemoved", !runtime.commands().availableFor(correlation));
                    receipt.addProperty("closedBridgeRemoved", runtime.commands().bridge(correlation, cancellation).isEmpty());
                    return tool.invokeAsync(context,
                            new RunJavascriptTool.Input("return commands.run('/me " + token + "_cancelled');", List.of()),
                            cancellation).handle((ignored, failure) -> {
                                Throwable cause = failure;
                                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null)
                                    cause = cause.getCause();
                                receipt.addProperty("cancelledReuseRejected", cause instanceof ModelClientException cancelled
                                        && "agent_cancelled".equals(cancelled.failure().code()));
                                if (!receipt.get("closedCapabilityRemoved").getAsBoolean()
                                        || !receipt.get("closedBridgeRemoved").getAsBoolean()
                                        || !receipt.get("cancelledReuseRejected").getAsBoolean())
                                    throw new IllegalStateException("Command cancellation/closure did not revoke the route");
                                return receipt;
                            });
                }).whenComplete((ignored, failure) -> {
                    cancellation.cancel();
                    if (failure != null) {
                        receipt.addProperty("outcome", "FAILED");
                        receipt.addProperty("failure", failure.toString());
                        client.execute(() -> complete.accept(receipt));
                        return;
                    }
                    server.execute(() -> {
                        try {
                            var player = server.getPlayerList().getPlayer(actor);
                            if (!server.isSameThread() || server != client.getSingleplayerServer() || server.isPublished()
                                    || player == null || GuideProbeWorldSettings.commandsAllowed(server)
                                    || server.getWorldData().getGameType() != net.minecraft.world.level.GameType.SURVIVAL)
                                throw new IllegalStateException("Native command warmup changed its no-grant world binding");
                            receipt.addProperty("serverReadbackOwnerThread", true);
                            receipt.addProperty("cheatsOffAfter", true);
                            receipt.addProperty("survivalAfter", true);
                            receipt.addProperty("outcome", "PASSED");
                        } catch (RuntimeException error) {
                            receipt.addProperty("outcome", "FAILED");
                            receipt.addProperty("failure", error.toString());
                        }
                        client.execute(() -> complete.accept(receipt));
                    });
                });
        return cancellation;
    }

    static void requireResults(JsonObject commands, UUID actor, String token) {
        if (!"help".equals(commands.get("helpNode").getAsString())
                || !"me <action>".equals(commands.get("messageNode").getAsString()))
            throw new IllegalStateException("Expected non-op help and signed-message command paths are missing");
        for (String name : List.of("help", "signed", "error")) {
            JsonObject result = commands.getAsJsonObject(name);
            if (!actor.toString().equals(result.get("actorId").getAsString())
                    || !"feedback".equals(result.get("state").getAsString())
                    || !result.get("feedbackObserved").getAsBoolean()
                    || result.getAsJsonArray("messages").isEmpty())
                throw new IllegalStateException("Actual native command feedback missing for " + name);
        }
        var help = commands.getAsJsonObject("help");
        var signed = commands.getAsJsonObject("signed");
        var error = commands.getAsJsonObject("error");
        if (!"help me".equals(help.get("command").getAsString())
                || !anyMessage(help.getAsJsonArray("messages"), "/me ")
                || !("me " + token).equals(signed.get("command").getAsString())
                || !anyMessage(signed.getAsJsonArray("messages"), token)
                || !("help " + token + "_missing").equals(error.get("command").getAsString())
                || error.get("messages").equals(help.get("messages"))
                || anyMessage(error.getAsJsonArray("messages"), "/me ")
                || help.get("sequence").getAsLong() + 1 != signed.get("sequence").getAsLong()
                || signed.get("sequence").getAsLong() + 1 != error.get("sequence").getAsLong())
            throw new IllegalStateException("Native help/signed-token/error feedback differs from submitted commands");
    }

    private static boolean anyMessage(JsonArray messages, String text) {
        for (var value : messages) if (value.getAsString().contains(text)) return true;
        return false;
    }
}
