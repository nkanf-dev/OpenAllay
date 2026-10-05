package dev.openallay.client.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;

/** Typed text owner. Screens register and focus widget(), the actual native input owner. */
public interface GuideMultilineEditor {
    AbstractWidget widget();
    String getValue();
    void setValue(String value, boolean bypassLineLimit);
    void setValueListener(Consumer<String> listener);
    void setCharacterLimit(int limit);
    void resize(int width, int height, int x, int y);
    default void setValue(String value) { setValue(value, false); }
}
