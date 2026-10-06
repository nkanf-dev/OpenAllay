package dev.openallay.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pre-rebuildWidgets native family: retire children/focus, then run the guarded screen initializer. */
abstract class GuideNativeScreenLifecycle extends Screen {
    protected GuideNativeScreenLifecycle(Component title) { super(title); }

    protected abstract void initGuideScreen();

    protected final void guideRebuildWidgets() {
        clearWidgets();
        GuideNativeFocus.clear(this);
        initGuideScreen();
    }
}
