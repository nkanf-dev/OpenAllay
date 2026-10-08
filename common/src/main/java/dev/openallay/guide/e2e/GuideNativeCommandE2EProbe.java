package dev.openallay.guide.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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


/** Separate development-only warmup; never injects feedback or executes server commands. */
final class GuideNativeCommandE2EProbe {
    private GuideNativeCommandE2EProbe() {}

    static CancellationSignal start(OpenAllayRuntime runtime,
            MinecraftGuideContextProvider contexts, UUID actor, Consumer<JsonObject> complete) {
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
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
        receipt.addProperty("submissionOwner", "MinecraftNativeCommandSubmission");
        receipt.addProperty("messageProof", "actor-bound-native-command-token-feedback");
        receipt.addProperty("cryptographicSignatureProof", false);
        RunJavascriptTool tool;
        ToolInvocationContext context;
        try {
            if (!Boolean.getBoolean(GuideClientE2EConfig.ENABLED) || !dev.openallay.client.gui.MinecraftClientWindow.ownerThread(client)
                    || client.player == null || !actor.equals(dev.openallay.client.gui.MinecraftClientWindow.actor(client)))
                throw new IllegalStateException("Native command warmup requires the development client owner and actor");
            dev.openallay.tool.Tool<?, ?> registered = runtime.tools().find(RunJavascriptTool.ID).orElseThrow();
            final class $oaPattern0_Holder { dev.openallay.tool.Tool<?, ?> value; RunJavascriptTool bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = registered) instanceof dev.openallay.tool.builtin.RunJavascriptTool && (($oaPattern0_holder.bound = (RunJavascriptTool) $oaPattern0_holder.value) != null))))
                throw new IllegalStateException("Actual registered JavaScript Tool is unavailable");
            tool = $oaPattern0_holder.bound;
            contexts.freezeRequest(correlation, true);
            dev.openallay.tool.ToolResult<dev.openallay.context.ToolInvocationContext> captured = contexts.capture(tool.descriptor().requiredContext(), correlation);
            final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.context.ToolInvocationContext> value; ToolResult.Success<ToolInvocationContext> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if (!((($oaPattern1_holder.value = captured) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern1_holder.bound = (ToolResult.Success<ToolInvocationContext>) $oaPattern1_holder.value) != null))))
                throw new IllegalStateException("Actual player context capture failed: " + captured);
            context = $oaPattern1_holder.bound.value();
            if (!context.unrestrictedJavascript() || !runtime.commands().availableFor(correlation)
                    || !actor.equals(context.player().orElseThrow().uuid()))
                throw new IllegalStateException("Native command warmup requires captured client-local full access");
            receipt.addProperty("unrestrictedCaptured", true);
            receipt.addProperty("commandOnlySetting", runtime.commands().enabled());
            receipt.addProperty("clientCaptureOwnerThread", dev.openallay.client.gui.MinecraftClientWindow.ownerThread(client));
        } catch (RuntimeException failure) {
            cancellation.cancel();
            contexts.closeRequest(correlation);
            receipt.addProperty("outcome", "FAILED");
            receipt.addProperty("failure", failure.toString());
            complete.accept(receipt);
            return cancellation;
        }
        net.minecraft.client.server.IntegratedServer server = dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client);
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
                var message = commands.run('/me %s');
                var error = commands.run('/help %s_missing');
                return JSON.stringify({helpNode: helpNode.path, messageNode: messageNode.path,
                  help: help, signed: message, error: error});
                """.formatted(token, token);
        // The real Tool owns its bounded command waits and its existing daemon worker.
        // Neither the client nor server owner thread waits on this future.
        tool.invokeAsync(context, new RunJavascriptTool.Input(source, dev.openallay.util.Java8Collections.listOf()), cancellation)
                .orTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .thenCompose(result -> {
                    final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<dev.openallay.tool.builtin.RunJavascriptTool.Output> value; ToolResult.Success<RunJavascriptTool.Output> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if (!((($oaPattern2_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern2_holder.bound = (ToolResult.Success<RunJavascriptTool.Output>) $oaPattern2_holder.value) != null))))
                        throw new IllegalStateException("Actual command Tool failed: " + result);
                    dev.openallay.tool.builtin.RunJavascriptTool.Output output = $oaPattern2_holder.bound.value();
                    if (!output.complete() || !"string".equals(output.resultType())
                            || output.preview() == null || !output.preview().isJsonPrimitive())
                        throw new IllegalStateException("Command warmup needs a complete scalar receipt");
                    com.google.gson.JsonObject commands = dev.openallay.json.JsonTrees.parse(output.preview().getAsString()).getAsJsonObject();
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
                            new RunJavascriptTool.Input("return commands.run('/me " + token + "_cancelled');", dev.openallay.util.Java8Collections.listOf()),
                            cancellation).handle((ignored, failure) -> {
                                Throwable cause = failure;
                                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null)
                                    cause = cause.getCause();
                                final class $oaPattern3_Holder { java.lang.Throwable value; ModelClientException bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
receipt.addProperty("cancelledReuseRejected", (($oaPattern3_holder.value = cause) instanceof dev.openallay.model.ModelClientException && (($oaPattern3_holder.bound = (ModelClientException) $oaPattern3_holder.value) != null))
                                        && "agent_cancelled".equals($oaPattern3_holder.bound.failure().code()));
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
                        dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> complete.accept(receipt));
                        return;
                    }
                    dev.openallay.server.NativeServerOwner.execute(server, () -> {
                        try {
                            net.minecraft.server.level.ServerPlayer player = dev.openallay.server.NativeServerOwner.player(server, actor);
                            if (!dev.openallay.server.NativeServerOwner.isOwner(server) || server != dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client) || dev.openallay.server.NativeServerOwner.published(server)
                                    || player == null || GuideProbeWorldSettings.commandsAllowed(server)
                                    || !dev.openallay.server.NativeServerOwner.survival(server))
                                throw new IllegalStateException("Native command warmup changed its no-grant world binding");
                            receipt.addProperty("serverReadbackOwnerThread", true);
                            receipt.addProperty("cheatsOffAfter", true);
                            receipt.addProperty("survivalAfter", true);
                            receipt.addProperty("outcome", "PASSED");
                        } catch (RuntimeException error) {
                            receipt.addProperty("outcome", "FAILED");
                            receipt.addProperty("failure", error.toString());
                        }
                        dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> complete.accept(receipt));
                    });
                });
        return cancellation;
    }

    static void requireResults(JsonObject commands, UUID actor, String token) {
        if (!"help".equals(commands.get("helpNode").getAsString())
                || !"me <action>".equals(commands.get("messageNode").getAsString()))
            throw new IllegalStateException("Expected non-op help and message command paths are missing");
        for (String name : dev.openallay.util.Java8Collections.listOf("help", "signed", "error")) {
            JsonObject result = commands.getAsJsonObject(name);
            if (!actor.toString().equals(result.get("actorId").getAsString())
                    || !"feedback".equals(result.get("state").getAsString())
                    || !result.get("feedbackObserved").getAsBoolean()
                    || (result.getAsJsonArray("messages").size() == 0))
                throw new IllegalStateException("Actual native command feedback missing for " + name);
        }
        com.google.gson.JsonObject help = commands.getAsJsonObject("help");
        com.google.gson.JsonObject signed = commands.getAsJsonObject("signed");
        com.google.gson.JsonObject error = commands.getAsJsonObject("error");
        if (!"help me".equals(help.get("command").getAsString())
                || !anyMessage(help.getAsJsonArray("messages"), "/me ")
                || !("me " + token).equals(signed.get("command").getAsString())
                || !anyMessage(signed.getAsJsonArray("messages"), token)
                || !("help " + token + "_missing").equals(error.get("command").getAsString())
                || error.get("messages").equals(help.get("messages"))
                || anyMessage(error.getAsJsonArray("messages"), "/me ")
                || help.get("sequence").getAsLong() + 1 != signed.get("sequence").getAsLong()
                || signed.get("sequence").getAsLong() + 1 != error.get("sequence").getAsLong())
            throw new IllegalStateException("Native help/message-token/error feedback differs from submitted commands");
    }

    private static boolean anyMessage(JsonArray messages, String text) {
        for (com.google.gson.JsonElement value : messages) if (value.getAsString().contains(text)) return true;
        return false;
    }
}
