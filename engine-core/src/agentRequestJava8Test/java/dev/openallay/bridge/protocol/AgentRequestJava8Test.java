package dev.openallay.bridge.protocol;
import org.junit.jupiter.api.Test;
final class AgentRequestJava8Test {
    @Test void actualActorBoundRequestAndWirePayload() throws Exception {
        AgentRequestJava8Fixture.main("1.8".equals(System.getProperty("java.specification.version")) ? new String[] {"java8"} : new String[0]);
    }
}
