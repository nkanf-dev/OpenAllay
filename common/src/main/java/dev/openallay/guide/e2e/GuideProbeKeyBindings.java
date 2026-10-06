package dev.openallay.guide.e2e;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Preserve and change the actual selected-native key identity in development fixtures. */
final class GuideProbeKeyBindings {
    private GuideProbeKeyBindings() {}
    static Runnable restoration(KeyMapping binding) {
        var key = InputConstants.getKey(binding.saveString());
        return () -> binding.setKey(key);
    }
    static String description(KeyMapping binding) { return binding.saveString(); }
    static void keyboard(KeyMapping binding, int key) {
        binding.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
    }
    static void unbind(KeyMapping binding) { binding.setKey(InputConstants.UNKNOWN); }
}
