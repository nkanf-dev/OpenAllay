package dev.openallay.trace.model;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.value.ValueSchemas;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class TraceStepAdmissionTest {
    private static final class Foreign implements TraceStep {}
    @Test void foreignStepCannotEnterTheImmutableTraceOrItsValueSchema() {
        Foreign foreign = new Foreign();
        assertThrows(IncompatibleClassChangeError.class, () -> new AgentTrace("trace", "message", Collections.emptySet(), Collections.singletonList(foreign)));
        assertThrows(IncompatibleClassChangeError.class, () -> ValueSchemas.of(AgentTrace.class).construct(new Object[]{"trace", "message", Collections.emptySet(), Collections.singletonList(foreign)}));
    }
    @Test void knownStepIdentityAndImmutableSnapshotStayExact() {
        AssistantMessageStep known = new AssistantMessageStep("answer");
        ArrayList<TraceStep> input = new ArrayList<>(); input.add(known);
        AgentTrace trace = new AgentTrace("trace", "message", Collections.emptySet(), input); input.clear();
        assertSame(known, trace.steps().get(0));
        assertThrows(UnsupportedOperationException.class, () -> trace.steps().add(known));
        assertThrows(NullPointerException.class, () -> new AgentTrace("trace", "message", Collections.emptySet(), Collections.singletonList(null)));
    }
}
