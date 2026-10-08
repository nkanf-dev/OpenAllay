package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/** Exact native regression fixture; no fake state/World/property implementation. */
public final class NativeActualStateRepairFixture {
    public static void run() {
        IBlockState dirt=Blocks.DIRT.getDefaultState();
        java.util.Collection<net.minecraft.block.properties.IProperty<?>> firstView=
                java.util.Collections.unmodifiableCollection(new java.util.ArrayList<>(dirt.getPropertyKeys()));
        java.util.Collection<net.minecraft.block.properties.IProperty<?>> secondView=
                java.util.Collections.unmodifiableCollection(new java.util.ArrayList<>(dirt.getPropertyKeys()));
        if(firstView==secondView || firstView.equals(secondView))
            throw new AssertionError("Regression fixture must use distinct Collection views with identity equality");
        if(!NativeActualStateRepair.samePropertyIdentities(firstView,secondView))
            throw new AssertionError("Identical native property identities in distinct collections must match");
        net.minecraft.block.properties.IProperty<? extends java.lang.Comparable<?>> originalSnowy=dirt.getBlock().getBlockState().getProperty("snowy");
        net.minecraft.block.properties.PropertyBool foreign=net.minecraft.block.properties.PropertyBool.create("snowy");
        if(foreign==originalSnowy || !foreign.equals(originalSnowy))
            throw new AssertionError("Fixture must contain equal native properties with different reference identities");
        java.util.ArrayList<net.minecraft.block.properties.IProperty<?>> wrong=new java.util.ArrayList<>(firstView);
        int replaced=0;
        for(int index=0;index<wrong.size();index++)if(wrong.get(index)==originalSnowy) { wrong.set(index,foreign);replaced++; }
        if(replaced!=1)throw new AssertionError("Fixture must replace exactly the original snowy property identity");
        if(NativeActualStateRepair.samePropertyIdentities(firstView,wrong))
            throw new AssertionError("A same-name replacement property identity must be rejected");
        Object snowProperty=dirt.getBlock().getBlockState().getProperty("snowy");
        final class $oaPattern0_Holder { java.lang.Object value; net.minecraft.block.properties.PropertyBool bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if(!((($oaPattern0_holder.value = snowProperty) instanceof net.minecraft.block.properties.PropertyBool && (($oaPattern0_holder.bound = (net.minecraft.block.properties.PropertyBool) $oaPattern0_holder.value) != null))))
            throw new AssertionError("Actual dirt snowy property missing");
        IBlockState snowyDirt=dirt.withProperty($oaPattern0_holder.bound,true);
        if(!NativeActualStateRepair.persisted(dirt,snowyDirt).equals(dirt))
            throw new AssertionError("Derived dirt snowy projection must retain original native metadata");
        IBlockState fence=Blocks.OAK_FENCE.getDefaultState();
        IBlockState connected=fence.withProperty(BlockFence.NORTH,true);
        if(!NativeActualStateRepair.persisted(fence,connected).equals(fence))
            throw new AssertionError("Derived fence connection must not become stored metadata");
        IBlockState stairs=Blocks.OAK_STAIRS.getDefaultState();
        IBlockState shaped=stairs.withProperty(BlockStairs.SHAPE,BlockStairs.EnumShape.INNER_LEFT);
        if(!NativeActualStateRepair.persisted(stairs,shaped).equals(stairs))
            throw new AssertionError("Derived stair shape must not become stored metadata");
        IBlockState facing=stairs.withProperty(BlockStairs.FACING,EnumFacing.WEST);
        if(!NativeActualStateRepair.persisted(stairs,facing).equals(facing))
            throw new AssertionError("Persisted stair facing must survive native normalization");
        IBlockState normalized=stairs.getBlock().getStateFromMeta(stairs.getBlock().getMetaFromState(facing));
        if(normalized!=facing)throw new AssertionError("Native metadata round trip must retain exact canonical state");
        try { NativeActualStateRepair.persisted(stairs,Blocks.STONE.getDefaultState()); }
        catch(ExtensionException expected) {
            if(!"unsupported_native_repair".equals(expected.code()))throw expected;
            return;
        }
        throw new AssertionError("Changed block identity must be rejected");
    }
}
