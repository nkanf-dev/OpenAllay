package dev.openallay.adapter.minecraft.v26_2.world;

import com.google.gson.JsonObject;
import dev.openallay.api.extension.WorldSession;
import java.nio.file.Path;
import java.util.Optional;
/** Native handles stay in the typed target leaf; this contract carries only detached values. */
interface WorldBinding {
    record Image(String actual,boolean changed) {}
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
