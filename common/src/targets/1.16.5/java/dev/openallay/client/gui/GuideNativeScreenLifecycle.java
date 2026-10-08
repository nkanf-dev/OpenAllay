package dev.openallay.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Exact old Screen collections; local rebuild is not a second native init event. */
abstract class GuideNativeScreenLifecycle extends Screen {
    protected GuideNativeScreenLifecycle(Component title) { super(title); }
    protected abstract void initGuideScreen();
    protected final void guideRebuildWidgets() {
        GuideNativeFocus.clear(this);
        setDragging(false);
        buttons.clear();
        children.clear();
        initGuideScreen();
    }
}
