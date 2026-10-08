package dev.openallay.agent.context;
import org.junit.jupiter.api.Test;
final class ContextCompactorJava8Test {
    @Test void actualBudgetSummaryAndResultProjection() throws Exception {
        ContextCompactorJava8Fixture.main("1.8".equals(System.getProperty("java.specification.version")) ? new String[] {"java8"} : new String[0]);
    }
}
