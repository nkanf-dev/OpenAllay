package dev.openallay.client.gui.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.openallay.client.gui.GuideNativeHintAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native old editor paint/narration hook for hint text; existing editor state stays native. */
@Mixin(EditBox.class)
public abstract class LegacyEditBoxHintMixin extends AbstractWidget implements GuideNativeHintAccess {
    @Shadow @Final private Font font;
    @Shadow private boolean bordered;
    @Shadow public abstract String getValue();
    @Unique private Component openallay$hint;
    protected LegacyEditBoxHintMixin(int x, int y, int width, int height, Component title) {
        super(x, y, width, height, title);
    }
    public final void openallay$hint(Component hint) { openallay$hint = hint; }
    @Inject(method = "renderButton(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V", at = @At("RETURN"))
    private void openallay$paintHint(PoseStack pose, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (openallay$hint == null || !getValue().isEmpty() || isFocused()) return;
        int left = x + (bordered ? 4 : 0), top = bordered ? y + (height - 8) / 2 : y;
        var text = font.substrByWidth(openallay$hint, Math.max(0, width - (bordered ? 8 : 0)));
        font.drawShadow(pose, net.minecraft.locale.Language.getInstance().getVisualOrder(text), left, top, 0xFF707070);
    }
    @Inject(method = "createNarrationMessage()Lnet/minecraft/network/chat/MutableComponent;", at = @At("RETURN"), cancellable = true, require = 1)
    private void openallay$narrateHint(CallbackInfoReturnable<net.minecraft.network.chat.MutableComponent> callback) {
        if (openallay$hint != null && getValue().isEmpty()) {
            callback.setReturnValue(callback.getReturnValue().append(dev.openallay.platform.minecraft.MinecraftComponents.literal(", ")).append(openallay$hint));
        }
    }
}
