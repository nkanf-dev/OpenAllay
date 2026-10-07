package dev.openallay.agent.session;

import com.google.gson.JsonObject;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ContextCheckpointCodec;
import dev.openallay.agent.context.ContextSourceHash;
import dev.openallay.agent.context.ContextStructure;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.json.EngineJson;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.tool.ToolResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Real synchronized session/context owners, original-modern vs genuineJava8. */
public final class AgentSessionJava8Fixture {
    private AgentSessionJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("java8")) equal("1.8", System.getProperty("java.specification.version"));
        UUID actor = uuid(1), request = uuid(2), steerId = uuid(3), nextRequest = uuid(4), controlId = uuid(5);
        AgentSessionKey key = new AgentSessionKey(actor, "session");
        AgentSessionKey otherActor = new AgentSessionKey(uuid(6), "session");
        AgentSessionStore store = new AgentSessionStore();
        List<ModelMessage> initial = Arrays.asList(ModelMessage.userText("first"));
        AgentSessionStore.Lease lease = success(store.reserveWithHistory(key, request, initial));
        check(store.status(key).active(), "reserved active");
        check(store.reserve(key, nextRequest) instanceof ToolResult.Failure, "exclusive lease");
        check(success(store.steer(key, request, steerId, ModelMessage.userText("instruction"))), "steer admitted");
        check(success(store.steer(key, request, steerId, ModelMessage.userText("replacement"))), "unconsumed steer replaced");
        check(!success(store.steer(otherActor, request, steerId, ModelMessage.userText("foreign"))), "actor isolated");
        List<AgentSessionStore.Steer> drained = store.drainSteers(lease);
        equal(1, drained.size()); equal("replacement", ((ModelContent.Text) drained.get(0).message().content().get(0)).text());
        check(!success(store.steer(key, request, steerId, ModelMessage.userText("replay"))), "consumed steer not replayed");
        check(!store.cancel(key, nextRequest), "stale request cannot stop");
        check(store.cancel(key, request), "exact request stopped");
        check(lease.cancellation().isCancelled(), "cancellation signal");
        check(!store.status(key).active(), "stop detached lease");
        AgentSessionStore.Lease successor = success(store.reserve(key, nextRequest));
        check(!store.finalizeCancelled(lease, initial), "old worker cannot overwrite successor");
        check(!store.finish(lease, initial), "old lease cannot finish successor");
        List<ModelMessage> completed = Arrays.asList(ModelMessage.userText("new"), assistant("done"));
        check(store.finish(successor, completed), "successor finished");
        equal(completed, store.history(key));
        AgentSessionStore.ControlLease control = success(store.reserveControl(key, controlId, null));
        check(store.ownsControl(control), "control owned");
        check(store.reserve(key, uuid(7)) instanceof ToolResult.Failure, "control excludes request");
        check(store.publishControl(control, Arrays.asList(ModelMessage.userText("projection"))), "control publication");
        check(!store.ownsControl(control), "published control released");
        AgentSessionStore.Lease checkpointLease = success(store.reserve(key, uuid(8)));
        List<ModelMessage> source = checkpointLease.history();
        String sourceHash = ContextSourceHash.compute(EngineJson.create(), source);
        ContextCheckpoint checkpoint = new ContextCheckpoint(uuid(9), 0, source.size(), sourceHash, "provider/model", Instant.EPOCH,
                ContextCheckpoint.Status.SUCCEEDED, "summary", null, null, 10);
        check(store.recordCheckpoint(checkpointLease, checkpoint), "checkpoint recorded");
        equal(Arrays.asList(checkpoint), store.checkpoints(key));
        check(store.finish(checkpointLease, source), "checkpoint lease finished");
        equal(Arrays.asList(checkpoint), store.checkpoints(key));
        ContextCheckpointCodec checkpointCodec = new ContextCheckpointCodec();
        equal(checkpoint, checkpointCodec.decode(checkpointCodec.encode(checkpoint)));
        System.out.println("checkpoint=" + checkpointCodec.encode(checkpoint));
        System.out.println("sourceHash=" + sourceHash);
        JsonObject arguments = new JsonObject(); arguments.addProperty("x", 1);
        ModelMessage toolUse = new ModelMessage(ModelRole.ASSISTANT, Arrays.<ModelContent>asList(new ModelContent.ToolUse("call", "test:tool", arguments)));
        ModelMessage toolResult = new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList(new ModelContent.ToolResult("call", arguments, false)));
        List<ModelMessage> messages = Arrays.asList(ModelMessage.userText("question"), toolUse, toolResult, assistant("answer"));
        List<ContextStructure.Unit> units = ContextStructure.units(messages);
        equal(3, units.size()); check(units.get(1).toolExchange(), "tool structural unit");
        ModelContextCodec codec = new ModelContextCodec();
        String encoded = codec.encode(messages); equal(messages, codec.decode(encoded));
        System.out.println("transcript=" + encoded);
        equal(messages, ContextStructure.summarySafe(messages));
        ModelMessage reasoning = new ModelMessage(ModelRole.ASSISTANT, Arrays.<ModelContent>asList(new ModelContent.Reasoning("private", "signature"), new ModelContent.Text("public")));
        equal(Arrays.asList(assistant("public")), ContextStructure.summarySafe(Arrays.asList(reasoning)));
        failure("orphan", () -> ContextStructure.units(Arrays.asList(toolResult)));
        failure("missingResult", () -> ContextStructure.units(Arrays.asList(toolUse)));
        failure("splitBoundary", () -> ContextStructure.requireBoundary(units, 2, messages.size()));
        failure("checkpointRange", () -> new ContextCheckpoint(uuid(9), 0, 0, sourceHash, "model", Instant.EPOCH, ContextCheckpoint.Status.SUCCEEDED, "summary", null, null, 0));
        failure("checkpointFailedShape", () -> new ContextCheckpoint(uuid(9), 0, 1, sourceHash, "model", Instant.EPOCH, ContextCheckpoint.Status.FAILED, "summary", "bad", "failure", 0));
        failure("codecExtra", () -> codec.decode("{\"messages\":[],\"extra\":true}"));
        failure("checkpointExtra", () -> checkpointCodec.decode(checkpointCodec.encode(checkpoint).replace("{", "{\"extra\":true,")));
        check(!store.hasContext(otherActor), "no cross actor context");
        store.hydrate(new AgentSessionKey(actor, "z"), initial); store.hydrate(new AgentSessionKey(actor, "a"), initial);
        equal(Arrays.asList("a", "session", "z"), dev.openallay.util.Java8Collections.toList(store.sessions(actor).stream().map(AgentSessionKey::sessionId)));
        store.clearActor(actor); equal(0, store.sessions(actor).size());
        System.out.println("PASS canonical lease epoch steering control and context source");
    }
    @SuppressWarnings("unchecked") private static <T> T success(ToolResult<T> result) {
        if (!(result instanceof ToolResult.Success)) throw new AssertionError("Expected success: " + result);
        return ((ToolResult.Success<T>) result).value();
    }
    private static ModelMessage assistant(String text) { return new ModelMessage(ModelRole.ASSISTANT, Arrays.<ModelContent>asList(new ModelContent.Text(text))); }
    private static UUID uuid(int value) { return new UUID(0, value); }
    private static void failure(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | IllegalStateException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
