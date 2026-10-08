package dev.openallay.settings.model;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.value.ValueSchemas;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class ModelConnectionResultAdmissionTest {
    private static final class Foreign implements ModelConnectionResult {}
    @Test void exactKnownVariantsAndNullableNotTestedState() {
        ModelConnectionResult.Success success = new ModelConnectionResult.Success("synthetic", ModelProtocol.OPENAI_CHAT, "https://synthetic.invalid", Instant.EPOCH, 3);
        ModelConnectionResult.Failure failure = new ModelConnectionResult.Failure("synthetic", "Synthetic failure");
        assertDoesNotThrow(() -> ModelConnectionResult.requireKnown(success));
        assertDoesNotThrow(() -> ModelConnectionResult.requireKnown(failure));
        assertDoesNotThrow(() -> ModelConnectionResult.requireKnown(null));
        IncompatibleClassChangeError foreign = assertThrows(IncompatibleClassChangeError.class,
                () -> ModelConnectionResult.requireKnown(new Foreign()));
        assertEquals("Unknown model connection result subtype", foreign.getMessage());
        assertTrue(java.lang.reflect.Modifier.isFinal(ModelConnectionResult.Success.class.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isFinal(ModelConnectionResult.Failure.class.getModifiers()));
    }
    @Test void constructorValidationAndOwnerWrittenSchemasStayExact() {
        assertThrows(IllegalArgumentException.class, () -> new ModelConnectionResult.Success(null, null, null, null, -1));
        assertThrows(NullPointerException.class, () -> new ModelConnectionResult.Success("synthetic", null, null, null, -1));
        assertThrows(IllegalArgumentException.class, () -> new ModelConnectionResult.Success("synthetic", ModelProtocol.OPENAI_CHAT, null, null, -1));
        assertThrows(NullPointerException.class, () -> new ModelConnectionResult.Success("synthetic", ModelProtocol.OPENAI_CHAT, "https://synthetic.invalid", null, -1));
        assertThrows(IllegalArgumentException.class, () -> new ModelConnectionResult.Success("synthetic", ModelProtocol.OPENAI_CHAT, "https://synthetic.invalid", Instant.EPOCH, -1));
        ModelConnectionResult.Success success = new ModelConnectionResult.Success("synthetic", ModelProtocol.OPENAI_CHAT, "https://synthetic.invalid", Instant.EPOCH, 3);
        ModelConnectionResult.Success rebuilt = ValueSchemas.of(ModelConnectionResult.Success.class).construct(new Object[] {"synthetic", ModelProtocol.OPENAI_CHAT, "https://synthetic.invalid", Instant.EPOCH, 3L});
        assertEquals(success, rebuilt); assertEquals(success.hashCode(), rebuilt.hashCode()); assertEquals(success.toString(), rebuilt.toString());
        ModelConnectionResult.Failure failure = new ModelConnectionResult.Failure("synthetic", "Synthetic failure");
        assertEquals(failure, ValueSchemas.of(ModelConnectionResult.Failure.class).construct(new Object[] {"synthetic", "Synthetic failure"}));
        assertEquals(31 * "synthetic".hashCode() + "Synthetic failure".hashCode(), failure.hashCode());
        assertThrows(IllegalArgumentException.class, () -> new ModelConnectionResult.Failure("\u2003", "Synthetic failure"));
        assertThrows(IllegalArgumentException.class, () -> new ModelConnectionResult.Failure("synthetic", "\u2003"));
    }
}
