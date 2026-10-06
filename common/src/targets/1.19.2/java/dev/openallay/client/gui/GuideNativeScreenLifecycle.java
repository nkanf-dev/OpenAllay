package dev.openallay.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native child rebuild keeps native focus and initialization behavior. */
abstract class GuideNativeScreenLifecycle extends Screen {
    protected GuideNativeScreenLifecycle(Component title) { super(title); }

    protected final void guideRebuildWidgets() {
        super.rebuildWidgets();
    }
}
