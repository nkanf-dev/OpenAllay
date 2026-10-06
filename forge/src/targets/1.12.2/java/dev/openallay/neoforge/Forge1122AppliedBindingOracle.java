package dev.openallay.neoforge;

import dev.openallay.client.gui.MinecraftTeardownState;
import dev.openallay.client.gui.GuideTextFieldDrawAccess;
import dev.openallay.client.context.GuideNativeSlotHitTest;
import dev.openallay.guide.e2e.GuideProbeMouseCallbacks;
import dev.openallay.adapter.minecraft.v26_2.world.mixin.NativeTileEntityStateAccessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Opt-in isolated actual targetbinding oracle; no mixin class is loaded as a probe. */
public final class Forge1122AppliedBindingOracle {
    private boolean done;
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("openallay.e2e.appliedBindings"))return;
        Minecraft client=Minecraft.getMinecraft();
        if(client.fontRenderer==null||client.world==null||client.player==null)return;
        done=true;
        try {
            if("builder-legacy-shapes".equals(System.getProperty("openallay.e2e.scenario")))
                dev.openallay.adapter.minecraft.v26_2.world.NativeActualStateRepairFixture.run();
            if(!(client instanceof MinecraftTeardownState)||((MinecraftTeardownState)client).openallay$teardownInProgress())
                throw new IllegalStateException("Real Minecraft teardown binding absent or wrong state");
            GuiTextField text=new GuiTextField(1,client.fontRenderer,0,0,100,20);
            if(!(text instanceof GuideTextFieldDrawAccess))throw new IllegalStateException("Real GuiTextField draw binding absent");
            GuideTextFieldDrawAccess access=(GuideTextFieldDrawAccess)text;
            access.openallay$drawText("binding-sentinel");
            if(!"binding-sentinel".equals(text.getText())||!"binding-sentinel".equals(access.openallay$drawText()))
                throw new IllegalStateException("Real GuiTextField member read/write diverged");
            GuiChest chest=new GuiChest(client.player.inventory,new InventoryBasic("chest-binding",false,27));
            chest.setWorldAndResolution(client,client.displayWidth,client.displayHeight);
            if(!(chest instanceof GuideNativeSlotHitTest)||!(chest instanceof GuideProbeMouseCallbacks))
                throw new IllegalStateException("Real GuiContainer slot/mouse bindings absent");
            // Real native hit method outside the screen must resolve no slot.
            if(((GuideNativeSlotHitTest)chest).openallay$getHoveredSlot(-1000,-1000)!=null)
                throw new IllegalStateException("Real native slot hit outside screen returned slot");
            TileEntityChest tile=new TileEntityChest();
            if(!(tile instanceof NativeTileEntityStateAccessor))throw new IllegalStateException("Real TileEntity cache accessor absent");
            NativeTileEntityStateAccessor state=(NativeTileEntityStateAccessor)tile;
            state.openallay$setBlockType(Blocks.CHEST);state.openallay$setBlockMetadata(0);
            if(tile.getBlockType()!=Blocks.CHEST||tile.getBlockMetadata()!=0)
                throw new IllegalStateException("Real TileEntity cache member readback diverged");
            String repairReceipt="builder-legacy-shapes".equals(System.getProperty("openallay.e2e.scenario")) ? "\"actualNativeRepairPropertyIdentities\":true," : "";
            String receipt="{"+repairReceipt+"\"accepted\":true,\"outcome\":\"PASSED\",\"actualMinecraftBinding\":true,\"actualGuiTextFieldReadWrite\":true,\"actualGuiContainerNativeHit\":true,\"actualGuiScreenMouseBinding\":true,\"actualTileEntityCacheReadback\":true,\"worldCatchObserverPending\":true}\n";
            Files.write(Paths.get(System.getProperty("openallay.e2e.appliedBindingsReceipt")),receipt.getBytes(StandardCharsets.UTF_8));
        } catch(Exception failure) {
            try{Files.write(Paths.get(System.getProperty("openallay.e2e.appliedBindingsReceipt")),("{\"accepted\":false,\"cause\":\""+failure.getClass().getName()+": "+failure.getMessage()+"\"}\n").getBytes(StandardCharsets.UTF_8));}
            catch(Exception write){failure.addSuppressed(write);}
            throw new IllegalStateException("Actual product mixin binding oracle failed",failure);
        }
    }
}
