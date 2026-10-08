package dev.openallay.bridge.protocol;

import org.junit.jupiter.api.Test;
final class BridgeHistoryJava8Test {
    @Test void actualHistoryImagesAndActorBoundTransport() throws Exception {
        BridgeHistoryJava8Fixture.main("1.8".equals(System.getProperty("java.specification.version")) ? new String[] {"java8"} : new String[0]);
    }
}
