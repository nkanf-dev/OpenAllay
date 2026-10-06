package dev.openallay.integration.jei;

import java.util.List;

/** Only semantic roles used by the shared provider, not publication slot geometry. */
record JeiIngredientSlot(Role role, List<JeiIngredientValue> values) {
    JeiIngredientSlot { values = List.copyOf(values); }
    enum Role { INPUT, OUTPUT, WORKSTATION, RENDER_ONLY }
}
