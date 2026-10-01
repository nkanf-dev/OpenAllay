package dev.openallay.script.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.RhinoJavascriptRuntime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class JavascriptCommandBridgeTest {
    private static final UUID ACTOR =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void disabledCapabilityHasNoBinding() {
        CommandCapabilityRuntime commands = runtime();
        commands.capture("request", ACTOR, catalog(), successful(commands, List.of()));

        assertFalse(commands.bridge("request", new CancellationSignal()).isPresent());
    }

    @Test
    void toggleIsFrozenForTheLifetimeOfEachRequest() {
        CommandCapabilityRuntime commands = runtime();
        commands.replace(new CommandCapabilityConfig(
                true));
        assertTrue(commands.freezeRequest("enabled-request"));

        commands.replace(CommandCapabilityConfig.defaults());
        commands.capture(
                "enabled-request", ACTOR, catalog(), successful(commands, List.of()));
        commands.capture(
                "disabled-request", ACTOR, catalog(), successful(commands, List.of()));

        assertTrue(commands.bridge("enabled-request", new CancellationSignal()).isPresent());
        assertFalse(commands.bridge("disabled-request", new CancellationSignal()).isPresent());

        commands.replace(new CommandCapabilityConfig(
                true));
        commands.capture(
                "disabled-request", ACTOR, catalog(), successful(commands, List.of()));
        assertFalse(commands.bridge("disabled-request", new CancellationSignal()).isPresent());
    }

    @Test
    void enabledCapabilityListsDescribesAndSubmitsInScriptOrder() {
        CommandCapabilityRuntime commands = runtime();
        commands.replace(new CommandCapabilityConfig(
                true));
        List<String> submitted = new ArrayList<>();
        commands.capture("request", ACTOR, catalog(), successful(commands, submitted));
        JavascriptCommandBridge bridge =
                commands.bridge("request", new CancellationSignal()).orElseThrow();

        JsonElement result = new RhinoJavascriptRuntime().execute(
                        """
                        var mod = commands.describe("examplemod run <target>");
                        var first = commands.run("/examplemod run @s");
                        var second = commands.run("say finished");
                        return {
                          node: mod,
                          sequences: [first.sequence, second.sequence],
                          commands: [first.command, second.command],
                          actor: first.actorId,
                          firstState: first.state,
                          firstMessages: first.messages
                        };
                        """,
                        Map.of(),
                        Map.of(),
                        new CancellationSignal(),
                        bridge)
                .value();

        assertEquals("argument", result.getAsJsonObject()
                .getAsJsonObject("node").get("kind").getAsString());
        assertEquals(List.of(1, 2), result.getAsJsonObject()
                .getAsJsonArray("sequences").asList().stream()
                .map(JsonElement::getAsInt).toList());
        assertEquals(List.of("examplemod run @s", "say finished"), submitted);
        assertEquals(ACTOR.toString(), result.getAsJsonObject().get("actor").getAsString());
        assertEquals("feedback", result.getAsJsonObject().get("firstState").getAsString());
        assertEquals(
                List.of("feedback: examplemod run @s"),
                result.getAsJsonObject().getAsJsonArray("firstMessages").asList().stream()
                        .map(JsonElement::getAsString).toList());
    }

    @Test
    void submittedCommandsRemainWhenLaterJavascriptFails() {
        CommandCapabilityRuntime commands = runtime();
        commands.replace(new CommandCapabilityConfig(
                true));
        List<String> submitted = new ArrayList<>();
        commands.capture("request", ACTOR, catalog(), successful(commands, submitted));

        assertThrows(
                JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        """
                        commands.run("say before-failure");
                        throw new Error("later failure");
                        """,
                        Map.of(),
                        Map.of(),
                        new CancellationSignal(),
                        commands.bridge("request", new CancellationSignal()).orElseThrow()));
        assertEquals(List.of("say before-failure"), submitted);
    }

    @Test
    void cancellationBeforeSubmissionSubmitsNothing() {
        CommandCapabilityRuntime commands = runtime();
        commands.replace(new CommandCapabilityConfig(
                true));
        List<String> submitted = new ArrayList<>();
        commands.capture("request", ACTOR, catalog(), successful(commands, submitted));
        CancellationSignal cancellation = new CancellationSignal();
        cancellation.cancel();

        assertThrows(
                dev.openallay.model.ModelClientException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "return commands.run('say never');",
                        Map.of(),
                        Map.of(),
                        cancellation,
                        commands.bridge("request", cancellation).orElseThrow()));
        assertTrue(submitted.isEmpty());
    }

    @Test
    void structuredMinecraftSubmissionFailuresReachTheToolBoundary() {
        CommandCapabilityRuntime commands = runtime();
        commands.replace(new CommandCapabilityConfig(
                true));
        commands.capture(
                "request",
                ACTOR,
                catalog(),
                (actor, command, cancellation) -> CompletableFuture.failedFuture(
                        new JavascriptExecutionException(
                                "command_connection_unavailable",
                                "Player command connection is unavailable")));

        JavascriptExecutionException failure = assertThrows(
                JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "return commands.run('say disconnected');",
                        Map.of(),
                        Map.of(),
                        new CancellationSignal(),
                        commands.bridge("request", new CancellationSignal()).orElseThrow()));

        assertEquals("command_connection_unavailable", failure.code());
    }

    @Test
    void commandWithoutGameMessageReturnsExplicitNoFeedbackState() {
        CommandCapabilityRuntime commands = runtime();
        commands.replace(new CommandCapabilityConfig(
                true));
        commands.capture(
                "request",
                ACTOR,
                catalog(),
                (actor, command, cancellation) -> CompletableFuture.completedFuture(null));

        JsonElement result = new RhinoJavascriptRuntime().execute(
                        "return commands.run('say silent');",
                        Map.of(),
                        Map.of(),
                        new CancellationSignal(),
                        commands.bridge("request", new CancellationSignal()).orElseThrow())
                .value();

        assertEquals("no_feedback", result.getAsJsonObject().get("state").getAsString());
        assertTrue(result.getAsJsonObject().getAsJsonArray("messages").isEmpty());
    }

    @Test
    void waitsForFeedbackThatArrivesAfterMinecraftAcceptedTheCommand() {
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime(5, 500);
        commands.replace(new CommandCapabilityConfig(
                true));
        commands.capture(
                "request",
                ACTOR,
                catalog(),
                (actor, command, cancellation) -> {
                    CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS)
                            .execute(() -> commands.acceptFeedback(
                                    actor, "delayed feedback: " + command));
                    return CompletableFuture.completedFuture(null);
                });

        JsonElement result = new RhinoJavascriptRuntime().execute(
                        "return commands.run('/version');",
                        Map.of(),
                        Map.of(),
                        new CancellationSignal(),
                        commands.bridge("request", new CancellationSignal()).orElseThrow())
                .value();

        assertEquals("feedback", result.getAsJsonObject().get("state").getAsString());
        assertEquals(
                List.of("delayed feedback: version"),
                result.getAsJsonObject().getAsJsonArray("messages").asList().stream()
                        .map(JsonElement::getAsString)
                        .toList());
    }

    @Test
    void parserAndPermissionRejectionsRemainObservedMinecraftFeedback() {
        CommandCapabilityRuntime commands = runtime();
        commands.replace(new CommandCapabilityConfig(
                true));
        commands.capture(
                "request",
                ACTOR,
                catalog(),
                (actor, command, cancellation) -> {
                    String message = command.startsWith("unknown")
                            ? "Unknown or incomplete command"
                            : "You do not have permission to use this command";
                    commands.acceptFeedback(actor, message);
                    return CompletableFuture.completedFuture(null);
                });

        JsonElement result = new RhinoJavascriptRuntime().execute(
                        """
                        return [
                          commands.run("unknown syntax"),
                          commands.run("op @s")
                        ];
                        """,
                        Map.of(),
                        Map.of(),
                        new CancellationSignal(),
                        commands.bridge("request", new CancellationSignal()).orElseThrow())
                .value();

        assertEquals("feedback", result.getAsJsonArray()
                .get(0).getAsJsonObject().get("state").getAsString());
        assertEquals(
                "Unknown or incomplete command",
                result.getAsJsonArray()
                        .get(0).getAsJsonObject()
                        .getAsJsonArray("messages").get(0).getAsString());
        assertEquals(
                "You do not have permission to use this command",
                result.getAsJsonArray()
                        .get(1).getAsJsonObject()
                        .getAsJsonArray("messages").get(0).getAsString());
    }

    @Test
    void samePlayerCommandsFromConcurrentSessionsOwnDisjointFeedbackWindows()
            throws Exception {
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime(10, 500);
        commands.replace(new CommandCapabilityConfig(
                true));
        AtomicInteger activeSubmissions = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CommandCapabilityRuntime.Submitter submitter = (actor, command, cancellation) -> {
            int active = activeSubmissions.incrementAndGet();
            maximumActive.accumulateAndGet(active, Math::max);
            CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS).execute(() -> {
                commands.acceptFeedback(actor, "feedback:" + command);
                activeSubmissions.decrementAndGet();
            });
            return CompletableFuture.completedFuture(null);
        };
        commands.capture("session-a", ACTOR, catalog(), submitter);
        commands.capture("session-b", ACTOR, catalog(), submitter);

        CompletableFuture<JsonElement> first = CompletableFuture.supplyAsync(
                () -> run(commands, "session-a", "say alpha", new CancellationSignal()));
        CompletableFuture<JsonElement> second = CompletableFuture.supplyAsync(
                () -> run(commands, "session-b", "say beta", new CancellationSignal()));

        List<String> messages = List.of(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS))
                .stream()
                .map(value -> value.getAsJsonObject()
                        .getAsJsonArray("messages").get(0).getAsString())
                .sorted()
                .toList();
        assertEquals(List.of("feedback:say alpha", "feedback:say beta"), messages);
        assertEquals(1, maximumActive.get());
    }

    @Test
    void cancellationWhileWaitingForThePlayerLockPreventsDispatch() throws Exception {
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime(10, 5_000);
        commands.replace(new CommandCapabilityConfig(
                true));
        CountDownLatch firstDispatched = new CountDownLatch(1);
        CompletableFuture<Void> firstSubmission = new CompletableFuture<>();
        AtomicInteger secondDispatches = new AtomicInteger();
        commands.capture(
                "session-a",
                ACTOR,
                catalog(),
                (actor, command, cancellation) -> {
                    firstDispatched.countDown();
                    return firstSubmission;
                });
        commands.capture(
                "session-b",
                ACTOR,
                catalog(),
                (actor, command, cancellation) -> {
                    secondDispatches.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                });
        CancellationSignal firstCancellation = new CancellationSignal();
        CancellationSignal secondCancellation = new CancellationSignal();
        CompletableFuture<JsonElement> first = CompletableFuture.supplyAsync(
                () -> run(commands, "session-a", "say first", firstCancellation));
        assertTrue(firstDispatched.await(1, TimeUnit.SECONDS));
        CompletableFuture<JsonElement> second = CompletableFuture.supplyAsync(
                () -> run(commands, "session-b", "say second", secondCancellation));

        secondCancellation.cancel();
        ExecutionException cancelled = assertThrows(
                ExecutionException.class,
                () -> second.get(1, TimeUnit.SECONDS));
        assertTrue(cancelled.getCause() instanceof dev.openallay.model.ModelClientException);
        assertEquals(0, secondDispatches.get());

        firstCancellation.cancel();
        assertThrows(ExecutionException.class, () -> first.get(1, TimeUnit.SECONDS));
    }

    @Test
    void completeCatalogRetainsModLiteralAndArgumentNodes() {
        CommandCatalogSnapshot catalog = catalog();

        assertTrue(catalog.nodes().stream().anyMatch(node ->
                node.path().equals("examplemod") && node.kind().equals("literal")));
        assertTrue(catalog.nodes().stream().anyMatch(node ->
                node.path().equals("examplemod run <target>")
                        && node.kind().equals("argument")
                        && node.argumentType().endsWith("EntityArgument")));
    }

    @Test
    void cancellationDuringFeedbackWaitStopsWaitingButDoesNotUndoSubmission() {
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime(5, 500);
        commands.replace(new CommandCapabilityConfig(
                true));
        List<String> submitted = new ArrayList<>();
        CancellationSignal cancellation = new CancellationSignal();
        commands.capture(
                "request",
                ACTOR,
                catalog(),
                (actor, command, ignoredCancellation) -> {
                    submitted.add(command);
                    CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS)
                            .execute(cancellation::cancel);
                    return CompletableFuture.completedFuture(null);
                });

        assertThrows(
                dev.openallay.model.ModelClientException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "return commands.run('say already-submitted');",
                        Map.of(),
                        Map.of(),
                        cancellation,
                        commands.bridge("request", cancellation).orElseThrow()));

        assertEquals(List.of("say already-submitted"), submitted);
    }

    private static CommandCapabilityRuntime runtime() {
        return new CommandCapabilityRuntime(5, 40);
    }

    private static JsonElement run(
            CommandCapabilityRuntime commands,
            String correlationId,
            String command,
            CancellationSignal cancellation) {
        return new RhinoJavascriptRuntime().execute(
                        "return commands.run(" + quote(command) + ");",
                        Map.of(),
                        Map.of(),
                        cancellation,
                        commands.bridge(correlationId, cancellation).orElseThrow())
                .value();
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static CommandCapabilityRuntime.Submitter successful(
            CommandCapabilityRuntime runtime, List<String> submitted) {
        return (actor, command, cancellation) -> {
            cancellation.throwIfCancelled();
            submitted.add(command);
            runtime.acceptFeedback(actor, "feedback: " + command);
            return CompletableFuture.completedFuture(null);
        };
    }

    private static CommandCatalogSnapshot catalog() {
        return new CommandCatalogSnapshot(
                Instant.parse("2026-07-25T00:00:00Z"),
                List.of(
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "say <message>",
                                "message",
                                "argument",
                                "com.mojang.brigadier.arguments.StringArgumentType",
                                true,
                                "",
                                List.of("<message>"),
                                List.of()),
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "examplemod",
                                "examplemod",
                                "literal",
                                "",
                                false,
                                "",
                                List.of("run <target>"),
                                List.of("run")),
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "examplemod run <target>",
                                "target",
                                "argument",
                                "net.minecraft.commands.arguments.EntityArgument",
                                true,
                                "",
                                List.of("<target>"),
                                List.of())));
    }
}
