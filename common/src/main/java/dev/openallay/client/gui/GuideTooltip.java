package dev.openallay.client.gui;

import java.util.Objects;


/** Tooltip intent; the selected native widget binds rendering and narration. */
@dev.openallay.value.ValueType(GuideTooltip.ValueSchemaProvider.class)
public final class GuideTooltip {
    private final net.minecraft.network.chat.Component text;
    public GuideTooltip(net.minecraft.network.chat.Component text) {
 Objects.requireNonNull(text, "text");
        this.text = text;
    }
    public net.minecraft.network.chat.Component text() { return text; }
public static GuideTooltip create(net.minecraft.network.chat.Component text) { return new GuideTooltip(text); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideTooltip)) return false;
        GuideTooltip that = (GuideTooltip) other;
        return java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "GuideTooltip[text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideTooltip> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideTooltip.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideTooltip>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideTooltip.class, "text", GuideTooltip::text)), arguments -> new GuideTooltip((net.minecraft.network.chat.Component) arguments[0]));
        }
    }
}
