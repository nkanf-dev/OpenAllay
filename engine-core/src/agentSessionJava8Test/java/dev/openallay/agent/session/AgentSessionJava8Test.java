package dev.openallay.agent.session;
import org.junit.jupiter.api.Test;
final class AgentSessionJava8Test {
    @Test void actualSessionEpochsAndCheckpointSource() throws Exception {
        AgentSessionJava8Fixture.main("1.8".equals(System.getProperty("java.specification.version")) ? new String[] {"java8"} : new String[0]);
    }
}
