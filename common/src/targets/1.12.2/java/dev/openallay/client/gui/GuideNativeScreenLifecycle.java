package dev.openallay.client.gui;

import net.minecraft.client.gui.GuiScreen;

/** Actual legacy screen base. 1.12 callback binding owns native init/resize/close entry points. */
abstract class GuideNativeScreenLifecycle extends GuiScreen {}
