package dev.openallay.guide.e2e;

/** Exact native fixture shape; SDK probe orchestration stays single-source. */
final class GuideProbeNativeWorldState {
    private GuideProbeNativeWorldState() {}
    static String chestWithDiamonds() { return "{\"id\":\"minecraft:chest\",\"properties\":{\"facing\":\"north\"},\"blockEntity\":\"{id:'minecraft:chest',Items:[{Slot:0b,id:'minecraft:diamond',Count:3b}]}\"}"; }
}
