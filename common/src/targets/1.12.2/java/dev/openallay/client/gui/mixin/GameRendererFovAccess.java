package dev.openallay.client.gui.mixin;
import dev.openallay.client.observation.GuideNativeCameraFov;
import dev.openallay.world.WorldFocusObservation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Samples native renderer results. No configured FOV or player-body rotation substitution. */
@Mixin(EntityRenderer.class)
public abstract class GameRendererFovAccess implements GuideNativeCameraFov {
    @Unique private long openallay$frame;
    @Unique private long openallay$fovFrame = -1;
    @Unique private long openallay$cameraFrame = -1;
    @Unique private float openallay$fov;
    @Unique private Object openallay$world;
    @Unique private WorldFocusObservation.Camera openallay$camera;
    @Override public final void openallay$beginFrame() { openallay$frame++; }
    @Inject(method="getFOVModifier(FZ)F", at=@At("RETURN"), require=1)
    private void openallay$fov(float partialTick, boolean useSetting, CallbackInfoReturnable<Float> callback) {
        if (useSetting) {
            openallay$fov = callback.getReturnValueF();
            openallay$fovFrame = openallay$frame;
        }
    }
    @Inject(method="setupCameraTransform(FI)V", at=@At("RETURN"), require=1)
    private void openallay$sample(float partialTick, int pass, CallbackInfo callback) {
        Minecraft client = Minecraft.getMinecraft();
        var entity = client.getRenderViewEntity();
        if (entity == null || client.world == null || openallay$fovFrame != openallay$frame) return;
        var values = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, values);
        // Invert the actual affine modelview, including native third-person collision and camera modifiers.
        double a=values.get(0), b=values.get(4), c=values.get(8);
        double d=values.get(1), e=values.get(5), f=values.get(9);
        double g=values.get(2), h=values.get(6), i=values.get(10);
        double determinant=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if (!Double.isFinite(determinant) || Math.abs(determinant)<1.0e-12) throw new IllegalStateException("Native camera matrix is singular");
        double ia=(e*i-f*h)/determinant, ib=(c*h-b*i)/determinant, ic=(b*f-c*e)/determinant;
        double id=(f*g-d*i)/determinant, ie=(a*i-c*g)/determinant, iff=(c*d-a*f)/determinant;
        double ig=(d*h-e*g)/determinant, ih=(b*g-a*h)/determinant, ii=(a*e-b*d)/determinant;
        double tx=values.get(12), ty=values.get(13), tz=values.get(14);
        double x=entity.prevPosX+(entity.posX-entity.prevPosX)*partialTick-ia*tx-ib*ty-ic*tz;
        double y=entity.prevPosY+(entity.posY-entity.prevPosY)*partialTick-id*tx-ie*ty-iff*tz;
        double z=entity.prevPosZ+(entity.posZ-entity.prevPosZ)*partialTick-ig*tx-ih*ty-ii*tz;
        float yaw=(float)Math.toDegrees(Math.atan2(ic,-ii));
        float pitch=(float)Math.toDegrees(Math.atan2(iff,Math.hypot(ic,ii)));
        String mode = client.gameSettings.thirdPersonView==0 ? "first_person" : client.gameSettings.thirdPersonView==1 ? "third_person_back" : "third_person_front";
        openallay$camera = new WorldFocusObservation.Camera(x,y,z,yaw,pitch,openallay$fov,mode,true,
                client.gameSettings.debugCamEnable,entity.getUniqueID());
        openallay$world=client.world;
        openallay$cameraFrame=openallay$frame;
    }
    @Override public final double openallay$observedFov() { return openallay$camera().fov(); }
    @Override public final WorldFocusObservation.Camera openallay$camera() {
        if (openallay$cameraFrame!=openallay$frame || openallay$world==null || Minecraft.getMinecraft().world!=openallay$world)
            throw new IllegalStateException("Native camera has not been rendered for this world");
        return openallay$camera;
    }
}
