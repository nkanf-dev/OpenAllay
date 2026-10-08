package dev.openallay.guide.e2e;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Preserve and change the actual selected-native key identity in development fixtures. */
final class GuideProbeKeyBindings {
    private GuideProbeKeyBindings() {}
    static void refresh() { KeyMapping.resetMapping(); }
    static void click(int key) { KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(key)); }

    static Runnable restoration(KeyMapping binding) {
        var key = InputConstants.getKey(binding.saveString());
        return () -> binding.setKey(key);
    }
    static String description(KeyMapping binding) { return binding.saveString(); }
    static boolean isKeyboard(KeyMapping binding, int key) { return InputConstants.getKey(binding.saveString()).equals(InputConstants.Type.KEYBOARD.getOrCreate(key)); }
    static void keyboard(KeyMapping binding, int key) {
        binding.setKey(InputConstants.Type.KEYBOARD.getOrCreate(key));
    }
    static void unbind(KeyMapping binding) { binding.setKey(InputConstants.UNKNOWN); }
}
