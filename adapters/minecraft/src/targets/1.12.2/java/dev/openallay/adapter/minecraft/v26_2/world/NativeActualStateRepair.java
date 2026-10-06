package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
/** Separates native persisted metadata from native neighbor-derived properties. */
final class NativeActualStateRepair {
    private NativeActualStateRepair() {}
    static IBlockState persisted(IBlockState current,IBlockState actual) {
        if(actual==null || actual.getBlock()!=current.getBlock() || !actual.getPropertyKeys().equals(current.getPropertyKeys()))
            throw new ExtensionException("unsupported_native_repair","Native actual-state changed its block/property domain");
        for(IProperty<?> property:actual.getPropertyKeys())requireAllowed(actual,property);
        Block block=current.getBlock();
        IBlockState normalized=block.getStateFromMeta(block.getMetaFromState(actual));
        if(normalized.getBlock()!=block || !normalized.getPropertyKeys().equals(current.getPropertyKeys())
                || !block.getStateFromMeta(block.getMetaFromState(normalized)).equals(normalized))
            throw new ExtensionException("unsupported_native_repair","Native metadata normalization did not retain its block/property domain");
        // Only projection-only differences are discarded here, after an actual native
        // query and exact metadata proof. They are never accepted as write input.
        return normalized;
    }
    private static <T extends Comparable<T>> void requireAllowed(IBlockState state,IProperty<T> property) {
        if(!property.getAllowedValues().contains(state.getValue(property)))
            throw new ExtensionException("unsupported_native_repair","Native actual-state produced an invalid property value: "+property.getName());
    }
}
