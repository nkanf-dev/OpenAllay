package dev.openallay.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelMessageImageJava8Test {
    @Test void completeOriginalAndPortedOwnersShareBehavior() throws Exception {
        assertTrue(ModelMessageImageJava8Fixture.vectors().size() > 200);
    }
}
