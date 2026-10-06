package dev.openallay.guide.e2e;

import com.google.gson.JsonObject;

/** Exact native fixture shape and JSON-escaped 1.12 SNBT string grammar. */
final class GuideProbeNativeWorldState {
    private GuideProbeNativeWorldState() {}
    static String chestWithDiamonds() {
        JsonObject state=new JsonObject();
        state.addProperty("id","minecraft:chest");
        JsonObject properties=new JsonObject();properties.addProperty("facing","north");
        state.add("properties",properties);
        state.addProperty("blockEntity","{id:\"minecraft:chest\",Items:[{Slot:0b,id:\"minecraft:diamond\",Count:3b}]}");
        return state.toString();
    }
}
