package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
/** Separates native persisted metadata from native neighbor-derived properties. */
final class NativeActualStateRepair {
    private NativeActualStateRepair() {}
    static IBlockState persisted(IBlockState current,IBlockState actual) {
        if(actual==null || actual.getBlock()!=current.getBlock() || !samePropertyDomain(actual,current))
            throw new ExtensionException("unsupported_native_repair","Native actual-state changed its block/property domain");
        for(IProperty<?> property:actual.getPropertyKeys())requireAllowed(actual,property);
        Block block=current.getBlock();
        IBlockState normalized=block.getStateFromMeta(block.getMetaFromState(actual));
        if(normalized.getBlock()!=block || !samePropertyDomain(normalized,current)
                || !block.getStateFromMeta(block.getMetaFromState(normalized)).equals(normalized))
            throw new ExtensionException("unsupported_native_repair","Native metadata normalization did not retain its block/property domain");
        for(IProperty<?> property:normalized.getPropertyKeys())requireAllowed(normalized,property);
        // Only projection-only differences are discarded here, after an actual native
        // query and exact metadata proof. They are never accepted as write input.
        return normalized;
    }
    /** Native getPropertyKeys promises Collection, not Set.equals or view identity. */
    private static boolean samePropertyDomain(IBlockState first,IBlockState second) {
        java.util.Collection<IProperty<?>> firstKeys=first.getPropertyKeys();
        java.util.Collection<IProperty<?>> secondKeys=second.getPropertyKeys();
        if(firstKeys.size()!=secondKeys.size())return false;
        java.util.Set<IProperty<?>> identities=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(IProperty<?> property:firstKeys)if(property==null || !identities.add(property))return false;
        for(IProperty<?> property:secondKeys)if(!identities.remove(property))return false;
        return identities.isEmpty();
    }
    private static <T extends Comparable<T>> void requireAllowed(IBlockState state,IProperty<T> property) {
        if(!property.getAllowedValues().contains(state.getValue(property)))
            throw new ExtensionException("unsupported_native_repair","Native actual-state produced an invalid property value: "+property.getName());
    }
}
