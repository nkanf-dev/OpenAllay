package dev.openallay.guide.e2e;

import net.minecraft.client.settings.KeyBinding;

/** LWJGL2 key bindings store actual integer key codes, not InputConstants.Key objects. */
final class GuideProbeKeyBindings {
    private GuideProbeKeyBindings() {}
    static Runnable restoration(KeyBinding binding) {
        int key = binding.getKeyCode();
        return () -> binding.setKeyCode(key);
    }
    static String description(KeyBinding binding) { return Integer.toString(binding.getKeyCode()); }
    static void keyboard(KeyBinding binding, int key) { binding.setKeyCode(key); }
    static void unbind(KeyBinding binding) { binding.setKeyCode(0); }
}
