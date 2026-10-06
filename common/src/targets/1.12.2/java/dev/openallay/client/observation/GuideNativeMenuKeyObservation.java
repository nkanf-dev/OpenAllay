package dev.openallay.client.observation;
import dev.openallay.client.gui.GuideNativeInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
/** Detached facts for one synchronous native keyboard dispatch; no input or focus mutation. */
public final class GuideNativeMenuKeyObservation {
    private static final GuideNativeMenuKeyObservation LISTENER = new GuideNativeMenuKeyObservation();
    private static boolean installed;
    private static GuiScreen dispatchScreen;
    private static boolean nativeTextFocused;
    private static boolean allowUserInputSuppressed;
    private GuideNativeMenuKeyObservation() {}
    public static synchronized void configure() {
        if (!installed) { MinecraftForge.EVENT_BUS.register(LISTENER); installed = true; }
    }
    public static void before(GuiScreen screen) {
        dispatchScreen = screen;
        nativeTextFocused = false;
    }
    public static void textField(boolean focused) {
        if (dispatchScreen != null && focused) nativeTextFocused = true;
    }
    /** This path has no current-event consumed result. Leave native handling untouched. */
    public static void afterAllowUserInput(Minecraft client, GuiScreen screen) {
        if (screen != null && client.currentScreen == screen) allowUserInputSuppressed = true;
    }
    public static String diagnostic() {
        return allowUserInputSuppressed ? "legacy_allow_user_input_consumption_unavailable" : "";
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void afterNativePost(GuiScreenEvent.KeyboardInputEvent.Post event) {
        Minecraft client = Minecraft.getMinecraft();
        GuiScreen screen = event.getGui();
        boolean textFocused = nativeTextFocused;
        boolean currentDispatch = dispatchScreen == screen;
        dispatchScreen = null;
        nativeTextFocused = false;
        if (!currentDispatch || screen == null || client.currentScreen != screen || textFocused
                || !Keyboard.getEventKeyState() || Keyboard.getEventKey() == Keyboard.KEY_NONE) return;
        ObservationMenuKeyHandler.afterUnhandledKey(client, 1,
                GuideNativeInput.capture(Keyboard.getEventKey(), 0, GuideNativeInput.modifiers()));
    }
}
