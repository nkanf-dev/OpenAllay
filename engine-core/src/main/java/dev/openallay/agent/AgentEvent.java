package dev.openallay.agent;

import com.google.gson.JsonObject;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.model.ModelEvent;
import java.util.List;
import java.util.Objects;

public interface AgentEvent {
    /** Closed Java8 event ingress; no foreign variants enter publication or receipt accounting. */
    static void requireKnown(AgentEvent event) {
        Objects.requireNonNull(event, "event");
        Class<?> type = event.getClass();
        if (type != StateChanged.class && type != RequestReleased.class && type != ContextCompacted.class && type != ContextUpdated.class && type != ContextFinalized.class && type != ModelUsageStarted.class && type != ModelUsageObserved.class && type != SteerApplied.class && type != SteerRejected.class && type != ModelProgress.class && type != ToolStarted.class && type != ToolCompleted.class && type != FinalText.class && type != Failed.class) {
            throw new IncompatibleClassChangeError("Unknown agent event subtype");
        }
    }
    @dev.openallay.value.ValueType(StateChanged.ValueSchemaProvider.class)
public static final class StateChanged implements AgentEvent {
    private final AgentState state;
    public StateChanged(AgentState state) {
        this.state = state;
    }
    public AgentState state() { return state; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StateChanged)) return false;
        StateChanged that = (StateChanged) other;
        return java.util.Objects.equals(state, that.state);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(state);
        return hash;
    }
    @Override public String toString() { return "StateChanged[state=" + state + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<StateChanged> schema() {
            return new dev.openallay.value.ValueSchema<>(StateChanged.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<StateChanged>>asList(new dev.openallay.value.ValueSchema.Component<>(StateChanged.class, "state", StateChanged::state)), arguments -> new StateChanged((AgentState) arguments[0]));
        }
    }
}

    /** Actual request cleanup and all dispatched call receipts have finished. */
    @dev.openallay.value.ValueType(RequestReleased.ValueSchemaProvider.class)
public static final class RequestReleased implements AgentEvent {
    public RequestReleased() {
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequestReleased)) return false;
        RequestReleased that = (RequestReleased) other;
        return true;
    }
    @Override public int hashCode() {
        int hash = 0;
        return hash;
    }
    @Override public String toString() { return "RequestReleased[]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequestReleased> schema() {
            return new dev.openallay.value.ValueSchema<>(RequestReleased.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequestReleased>>asList(), arguments -> new RequestReleased());
        }
    }
}

    @dev.openallay.value.ValueType(ContextCompacted.ValueSchemaProvider.class)
public static final class ContextCompacted implements AgentEvent {
    private final ContextCheckpoint checkpoint;
    public ContextCompacted(ContextCheckpoint checkpoint) {

            Objects.requireNonNull(checkpoint, "checkpoint");

        this.checkpoint = checkpoint;
    }
    public ContextCheckpoint checkpoint() { return checkpoint; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContextCompacted)) return false;
        ContextCompacted that = (ContextCompacted) other;
        return java.util.Objects.equals(checkpoint, that.checkpoint);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(checkpoint);
        return hash;
    }
    @Override public String toString() { return "ContextCompacted[checkpoint=" + checkpoint + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContextCompacted> schema() {
            return new dev.openallay.value.ValueSchema<>(ContextCompacted.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContextCompacted>>asList(new dev.openallay.value.ValueSchema.Component<>(ContextCompacted.class, "checkpoint", ContextCompacted::checkpoint)), arguments -> new ContextCompacted((ContextCheckpoint) arguments[0]));
        }
    }
}

    /** Actual model messages, distinct from the player-facing timeline. */
    @dev.openallay.value.ValueType(ContextUpdated.ValueSchemaProvider.class)
public static final class ContextUpdated implements AgentEvent {
    private final List<dev.openallay.model.ModelMessage> messages;
    private final List<dev.openallay.model.ModelMessage> requestMessages;
    public ContextUpdated(List<dev.openallay.model.ModelMessage> messages, List<dev.openallay.model.ModelMessage> requestMessages) {

            messages = dev.openallay.agent.context.ModelContextCodec.safe(messages);
            requestMessages = dev.openallay.agent.context.ModelContextCodec.safe(requestMessages);

        this.messages = messages;
        this.requestMessages = requestMessages;
    }
    public List<dev.openallay.model.ModelMessage> messages() { return messages; }
    public List<dev.openallay.model.ModelMessage> requestMessages() { return requestMessages; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContextUpdated)) return false;
        ContextUpdated that = (ContextUpdated) other;
        return java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(requestMessages, that.requestMessages);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(requestMessages);
        return hash;
    }
    @Override public String toString() { return "ContextUpdated[messages=" + messages + ", requestMessages=" + requestMessages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContextUpdated> schema() {
            return new dev.openallay.value.ValueSchema<>(ContextUpdated.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContextUpdated>>asList(new dev.openallay.value.ValueSchema.Component<>(ContextUpdated.class, "messages", ContextUpdated::messages), new dev.openallay.value.ValueSchema.Component<>(ContextUpdated.class, "requestMessages", ContextUpdated::requestMessages)), arguments -> new ContextUpdated((List) arguments[0], (List) arguments[1]));
        }
    }
}

    /** One final safe handoff may archive a visibly cancelled request without reopening its UI. */
    @dev.openallay.value.ValueType(ContextFinalized.ValueSchemaProvider.class)
public static final class ContextFinalized implements AgentEvent {
    private final List<dev.openallay.model.ModelMessage> messages;
    private final List<dev.openallay.model.ModelMessage> requestMessages;
    public ContextFinalized(List<dev.openallay.model.ModelMessage> messages, List<dev.openallay.model.ModelMessage> requestMessages) {

            messages = dev.openallay.agent.context.ModelContextCodec.safe(messages);
            requestMessages = dev.openallay.agent.context.ModelContextCodec.safe(requestMessages);

        this.messages = messages;
        this.requestMessages = requestMessages;
    }
    public List<dev.openallay.model.ModelMessage> messages() { return messages; }
    public List<dev.openallay.model.ModelMessage> requestMessages() { return requestMessages; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContextFinalized)) return false;
        ContextFinalized that = (ContextFinalized) other;
        return java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(requestMessages, that.requestMessages);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(requestMessages);
        return hash;
    }
    @Override public String toString() { return "ContextFinalized[messages=" + messages + ", requestMessages=" + requestMessages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContextFinalized> schema() {
            return new dev.openallay.value.ValueSchema<>(ContextFinalized.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContextFinalized>>asList(new dev.openallay.value.ValueSchema.Component<>(ContextFinalized.class, "messages", ContextFinalized::messages), new dev.openallay.value.ValueSchema.Component<>(ContextFinalized.class, "requestMessages", ContextFinalized::requestMessages)), arguments -> new ContextFinalized((List) arguments[0], (List) arguments[1]));
        }
    }
}

    /** Actual dispatched call, including summary calls. Never emitted for local preflight failure. */
    @dev.openallay.value.ValueType(ModelUsageStarted.ValueSchemaProvider.class)
public static final class ModelUsageStarted implements AgentEvent {
    private final java.util.UUID callId;
    private final String modelIdentifier;
    public ModelUsageStarted(java.util.UUID callId, String modelIdentifier) {

            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(modelIdentifier, "modelIdentifier");

        this.callId = callId;
        this.modelIdentifier = modelIdentifier;
    }
    public java.util.UUID callId() { return callId; }
    public String modelIdentifier() { return modelIdentifier; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelUsageStarted)) return false;
        ModelUsageStarted that = (ModelUsageStarted) other;
        return java.util.Objects.equals(callId, that.callId) && java.util.Objects.equals(modelIdentifier, that.modelIdentifier);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(callId);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        return hash;
    }
    @Override public String toString() { return "ModelUsageStarted[callId=" + callId + ", modelIdentifier=" + modelIdentifier + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelUsageStarted> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelUsageStarted.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelUsageStarted>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelUsageStarted.class, "callId", ModelUsageStarted::callId), new dev.openallay.value.ValueSchema.Component<>(ModelUsageStarted.class, "modelIdentifier", ModelUsageStarted::modelIdentifier)), arguments -> new ModelUsageStarted((java.util.UUID) arguments[0], (String) arguments[1]));
        }
    }
}

    /** One immutable provider-attempt receipt; no response text or private reasoning. */
    @dev.openallay.value.ValueType(ModelUsageObserved.ValueSchemaProvider.class)
public static final class ModelUsageObserved implements AgentEvent {
    private final java.util.UUID callId;
    private final String modelIdentifier;
    private final dev.openallay.model.ModelUsage usage;
    public ModelUsageObserved(java.util.UUID callId, String modelIdentifier, dev.openallay.model.ModelUsage usage) {

            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(modelIdentifier, "modelIdentifier");
            Objects.requireNonNull(usage, "usage");

        this.callId = callId;
        this.modelIdentifier = modelIdentifier;
        this.usage = usage;
    }
    public java.util.UUID callId() { return callId; }
    public String modelIdentifier() { return modelIdentifier; }
    public dev.openallay.model.ModelUsage usage() { return usage; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelUsageObserved)) return false;
        ModelUsageObserved that = (ModelUsageObserved) other;
        return java.util.Objects.equals(callId, that.callId) && java.util.Objects.equals(modelIdentifier, that.modelIdentifier) && java.util.Objects.equals(usage, that.usage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(callId);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        return hash;
    }
    @Override public String toString() { return "ModelUsageObserved[callId=" + callId + ", modelIdentifier=" + modelIdentifier + ", usage=" + usage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelUsageObserved> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelUsageObserved.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelUsageObserved>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelUsageObserved.class, "callId", ModelUsageObserved::callId), new dev.openallay.value.ValueSchema.Component<>(ModelUsageObserved.class, "modelIdentifier", ModelUsageObserved::modelIdentifier), new dev.openallay.value.ValueSchema.Component<>(ModelUsageObserved.class, "usage", ModelUsageObserved::usage)), arguments -> new ModelUsageObserved((java.util.UUID) arguments[0], (String) arguments[1], (dev.openallay.model.ModelUsage) arguments[2]));
        }
    }
}

    /** The supplemental user message entered a complete structural model boundary. */
    @dev.openallay.value.ValueType(SteerApplied.ValueSchemaProvider.class)
public static final class SteerApplied implements AgentEvent {
    private final java.util.UUID messageId;
    private final dev.openallay.model.ModelMessage message;
    public SteerApplied(java.util.UUID messageId, dev.openallay.model.ModelMessage message) {

            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(message, "message");
            if (message.role() != dev.openallay.model.ModelRole.USER) {
                throw new IllegalArgumentException("steer must be a user message");
            }

        this.messageId = messageId;
        this.message = message;
    }
    public java.util.UUID messageId() { return messageId; }
    public dev.openallay.model.ModelMessage message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SteerApplied)) return false;
        SteerApplied that = (SteerApplied) other;
        return java.util.Objects.equals(messageId, that.messageId) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "SteerApplied[messageId=" + messageId + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SteerApplied> schema() {
            return new dev.openallay.value.ValueSchema<>(SteerApplied.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SteerApplied>>asList(new dev.openallay.value.ValueSchema.Component<>(SteerApplied.class, "messageId", SteerApplied::messageId), new dev.openallay.value.ValueSchema.Component<>(SteerApplied.class, "message", SteerApplied::message)), arguments -> new SteerApplied((java.util.UUID) arguments[0], (dev.openallay.model.ModelMessage) arguments[1]));
        }
    }
}

    /** A sealed/released request cannot accept this instruction; retain it as follow-up. */
    @dev.openallay.value.ValueType(SteerRejected.ValueSchemaProvider.class)
public static final class SteerRejected implements AgentEvent {
    private final java.util.UUID messageId;
    public SteerRejected(java.util.UUID messageId) {
 Objects.requireNonNull(messageId, "messageId");
        this.messageId = messageId;
    }
    public java.util.UUID messageId() { return messageId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SteerRejected)) return false;
        SteerRejected that = (SteerRejected) other;
        return java.util.Objects.equals(messageId, that.messageId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        return hash;
    }
    @Override public String toString() { return "SteerRejected[messageId=" + messageId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SteerRejected> schema() {
            return new dev.openallay.value.ValueSchema<>(SteerRejected.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SteerRejected>>asList(new dev.openallay.value.ValueSchema.Component<>(SteerRejected.class, "messageId", SteerRejected::messageId)), arguments -> new SteerRejected((java.util.UUID) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(ModelProgress.ValueSchemaProvider.class)
public static final class ModelProgress implements AgentEvent {
    private final ModelEvent event;
    public ModelProgress(ModelEvent event) {

            ModelEvent.requireKnown(event);
            Objects.requireNonNull(event, "event");
            if (event instanceof ModelEvent.ReasoningDelta) {
                event = new ModelEvent.ReasoningDelta("");
            }

        this.event = event;
    }
    public ModelEvent event() { return event; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelProgress)) return false;
        ModelProgress that = (ModelProgress) other;
        return java.util.Objects.equals(event, that.event);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(event);
        return hash;
    }
    @Override public String toString() { return "ModelProgress[event=" + event + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelProgress> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelProgress.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelProgress>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelProgress.class, "event", ModelProgress::event)), arguments -> new ModelProgress((ModelEvent) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(ToolStarted.ValueSchemaProvider.class)
public static final class ToolStarted implements AgentEvent {
    private final String invocationId;
    private final String toolId;
    private final JsonObject arguments;
    private final List<GuideToolMessage> presentationMessages;
    public ToolStarted(String invocationId, String toolId, JsonObject arguments, List<GuideToolMessage> presentationMessages) {

            requireIdentity(invocationId, toolId);
            arguments = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(arguments, "arguments"));
            presentationMessages = dev.openallay.util.Java8Collections.listCopyOf(presentationMessages);

        this.invocationId = invocationId;
        this.toolId = toolId;
        this.arguments = arguments;
        this.presentationMessages = presentationMessages;
    }
    public String invocationId() { return invocationId; }
    public String toolId() { return toolId; }
    public List<GuideToolMessage> presentationMessages() { return presentationMessages; }
public ToolStarted(
                String invocationId,
                String toolId,
                List<GuideToolMessage> presentationMessages) {
            this(invocationId, toolId, new JsonObject(), presentationMessages);
        }
public ToolStarted(String invocationId, String toolId) {
            this(invocationId, toolId, new JsonObject(), dev.openallay.util.Java8Collections.listOf());
        }

        public JsonObject arguments() {
            return dev.openallay.json.JsonTrees.copy(arguments);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolStarted)) return false;
        ToolStarted that = (ToolStarted) other;
        return java.util.Objects.equals(invocationId, that.invocationId) && java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(arguments, that.arguments) && java.util.Objects.equals(presentationMessages, that.presentationMessages);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(invocationId);
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(arguments);
        hash = 31 * hash + java.util.Objects.hashCode(presentationMessages);
        return hash;
    }
    @Override public String toString() { return "ToolStarted[invocationId=" + invocationId + ", toolId=" + toolId + ", arguments=" + arguments + ", presentationMessages=" + presentationMessages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolStarted> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolStarted.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolStarted>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolStarted.class, "invocationId", ToolStarted::invocationId), new dev.openallay.value.ValueSchema.Component<>(ToolStarted.class, "toolId", ToolStarted::toolId), new dev.openallay.value.ValueSchema.Component<>(ToolStarted.class, "arguments", ToolStarted::arguments), new dev.openallay.value.ValueSchema.Component<>(ToolStarted.class, "presentationMessages", ToolStarted::presentationMessages)), arguments -> new ToolStarted((String) arguments[0], (String) arguments[1], (JsonObject) arguments[2], (List) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(ToolCompleted.ValueSchemaProvider.class)
public static final class ToolCompleted implements AgentEvent {
    private final String invocationId;
    private final String toolId;
    private final boolean failure;
    private final JsonObject normalized;
    public ToolCompleted(String invocationId, String toolId, boolean failure, JsonObject normalized) {

            requireIdentity(invocationId, toolId);
            normalized = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(normalized, "normalized"));

        this.invocationId = invocationId;
        this.toolId = toolId;
        this.failure = failure;
        this.normalized = normalized;
    }
    public String invocationId() { return invocationId; }
    public String toolId() { return toolId; }
    public boolean failure() { return failure; }

        public JsonObject normalized() {
            return dev.openallay.json.JsonTrees.copy(normalized);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolCompleted)) return false;
        ToolCompleted that = (ToolCompleted) other;
        return java.util.Objects.equals(invocationId, that.invocationId) && java.util.Objects.equals(toolId, that.toolId) && failure == that.failure && java.util.Objects.equals(normalized, that.normalized);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(invocationId);
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + Boolean.hashCode(failure);
        hash = 31 * hash + java.util.Objects.hashCode(normalized);
        return hash;
    }
    @Override public String toString() { return "ToolCompleted[invocationId=" + invocationId + ", toolId=" + toolId + ", failure=" + failure + ", normalized=" + normalized + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolCompleted> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolCompleted.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolCompleted>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolCompleted.class, "invocationId", ToolCompleted::invocationId), new dev.openallay.value.ValueSchema.Component<>(ToolCompleted.class, "toolId", ToolCompleted::toolId), new dev.openallay.value.ValueSchema.Component<>(ToolCompleted.class, "failure", ToolCompleted::failure), new dev.openallay.value.ValueSchema.Component<>(ToolCompleted.class, "normalized", ToolCompleted::normalized)), arguments -> new ToolCompleted((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (JsonObject) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(FinalText.ValueSchemaProvider.class)
public static final class FinalText implements AgentEvent {
    private final String text;
    public FinalText(String text) {
        this.text = text;
    }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FinalText)) return false;
        FinalText that = (FinalText) other;
        return java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "FinalText[text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<FinalText> schema() {
            return new dev.openallay.value.ValueSchema<>(FinalText.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<FinalText>>asList(new dev.openallay.value.ValueSchema.Component<>(FinalText.class, "text", FinalText::text)), arguments -> new FinalText((String) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(Failed.ValueSchemaProvider.class)
public static final class Failed implements AgentEvent {
    private final String code;
    private final String message;
    public Failed(String code, String message) {
        this.code = code;
        this.message = message;
    }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Failed)) return false;
        Failed that = (Failed) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "Failed[code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Failed> schema() {
            return new dev.openallay.value.ValueSchema<>(Failed.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Failed>>asList(new dev.openallay.value.ValueSchema.Component<>(Failed.class, "code", Failed::code), new dev.openallay.value.ValueSchema.Component<>(Failed.class, "message", Failed::message)), arguments -> new Failed((String) arguments[0], (String) arguments[1]));
        }
    }
}

    static void requireIdentity(String invocationId, String toolId) {
        if (invocationId == null || dev.openallay.util.Java8Strings.isBlank(invocationId)
                || toolId == null || dev.openallay.util.Java8Strings.isBlank(toolId)) {
            throw new IllegalArgumentException("tool invocation identity is required");
        }
    }
}
