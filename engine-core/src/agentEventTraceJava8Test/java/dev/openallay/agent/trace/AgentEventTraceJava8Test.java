package dev.openallay.agent.trace;
import org.junit.jupiter.api.Test;
final class AgentEventTraceJava8Test {
    @Test void actualClosedEventsAndTracePersistence() throws Exception {
        AgentEventTraceJava8Fixture.main("1.8".equals(System.getProperty("java.specification.version")) ? new String[] {"java8"} : new String[0]);
    }
}
