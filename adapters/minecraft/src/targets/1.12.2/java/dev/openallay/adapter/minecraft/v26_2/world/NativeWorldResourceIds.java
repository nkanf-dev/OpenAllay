package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.util.ResourceLocation;
/** Real 1.12 identifiers; reject lossy normalization and malformed input. */
final class NativeWorldResourceIds {
    private NativeWorldResourceIds() {}
    static ResourceLocation tryParse(String value) {
        if(value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return null;
        ResourceLocation id = new ResourceLocation(value);
        return value.equals(id.toString()) ? id : null;
    }
    static ResourceLocation parse(String value, String field) {
        ResourceLocation id=tryParse(value);
        if(id==null) throw new IllegalArgumentException("Invalid "+field+": "+value);
        return id;
    }
    static ResourceLocation fromNamespaceAndPath(String namespace,String path) {
        return parse(namespace+":"+path,"resource id");
    }
}
