package dev.openallay.adapter.minecraft.v26_2.world;

import com.google.gson.JsonObject;
import dev.openallay.api.extension.WorldSession;
import java.nio.file.Path;
import java.util.Optional;
/** Native handles stay in the typed target leaf; this contract carries only detached values. */
interface WorldBinding {
    @dev.openallay.value.ValueType(Image.ValueSchemaProvider.class)
public static final class Image {
    private final String actual;
    private final boolean changed;
    public Image(String actual, boolean changed) {
        this.actual = actual;
        this.changed = changed;
    }
    public String actual() { return actual; }
    public boolean changed() { return changed; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Image)) return false;
        Image that = (Image) other;
        return java.util.Objects.equals(actual, that.actual) && changed == that.changed;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actual);
        hash = 31 * hash + Boolean.hashCode(changed);
        return hash;
    }
    @Override public String toString() { return "Image[actual=" + actual + ", changed=" + changed + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Image> schema() {
            return new dev.openallay.value.ValueSchema<>(Image.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Image>>asList(new dev.openallay.value.ValueSchema.Component<>(Image.class, "actual", Image::actual), new dev.openallay.value.ValueSchema.Component<>(Image.class, "changed", Image::changed)), arguments -> new Image((String) arguments[0], (Boolean) arguments[1]));
        }
    }
}
    OwnerThreadBridge.Owner clientOwner();
    OwnerThreadBridge.Owner serverOwner();
    OwnerThreadBridge.Owner serverCaptureOwner();
    void captureServer();
    void validateServer();
    void beginAction();
    void endAction();
    void invalidateProofs();
    void validatePosition(int x,int y,int z);
    String context();
    String read(int x,int y,int z);
    boolean canonicalAirAt(int x,int y,int z);
    boolean canonicalAir(int minX,int minY,int minZ,int maxX,int maxY,int maxZ);
    String terrainState(int x,int y,int z);
    int terrainTop(int x,int z,int minY,int maxY);
    String preview(int x,int y,int z,String state);
    Image write(int x,int y,int z,String state,Runnable requireActive);
    boolean sameImage(String before,String actual);
    String transform(String state,int degrees,String mirror);
    WorldSession.RepairOutcome repair(int x,int y,int z,Runnable requireActive);
    void notifyNeighbours(int x,int y,int z,Runnable requireActive);
    String dimension();
    String createWorldId();
    Optional<String> existingWorldId();
    Path artifacts();
    boolean isOwnerThread();
}
