package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionInvocation;
import dev.openallay.api.extension.WorldSession;
import java.nio.file.Path;
import java.util.Optional;
/** One owner admission, revocation, failure/readback and world-identity algorithm for every target. */
final class NativeWorldSession implements WorldSession {
    private final WorldBinding binding;
    private final OwnerThreadBridge bridge;
    private boolean ownerAction;
    private String worldId;
    private NativeWorldSession(WorldBinding binding,OwnerThreadBridge bridge) {
        this.binding=binding; this.bridge=bridge;
    }
    static NativeWorldSession capture(OwnerThreadBridge bridge,ExtensionInvocation invocation) {
        WorldBinding binding=bridge.call(NativeWorldBinding.captureOwner(),()->NativeWorldBinding.captureClient(invocation));
        NativeWorldSession session=new NativeWorldSession(binding,bridge);
        bridge.callAfter(binding.clientOwner(),binding.serverCaptureOwner(),()->{binding.captureServer();return null;});
        return session;
    }
    @Override public <T> T call(java.util.concurrent.Callable<T> action) {
        return bridge.callAfter(binding.clientOwner(),binding.serverOwner(),()->{
            ownerAction=true;
            try { binding.beginAction(); return action.call(); }
            finally { ownerAction=false; binding.endAction(); }
        });
    }
    private void requireOwnerAction() {
        bridge.checkActive(); binding.validateServer();
        if(!ownerAction) throw new dev.openallay.api.extension.ExtensionException("owner_action_required","Native operations require an admitted owner action");
    }
    @Override public long sliceDeadline() { requireOwnerAction(); return System.nanoTime()+4_000_000L; }
    @Override public long dispatches() { bridge.checkActive(); return bridge.dispatches(); }
    @Override public void validatePosition(int x,int y,int z) { requireOwnerAction(); binding.validatePosition(x,y,z); }
    @Override public String context() { return call(binding::context); }
    @Override public String read(int x,int y,int z) {
        validatePosition(x,y,z);
        try { return binding.read(x,y,z); } finally { binding.invalidateProofs(); }
    }
    @Override public String readNonAir(int x,int y,int z) {
        validatePosition(x,y,z);
        if(binding.canonicalAirAt(x,y,z)) return null;
        try { return binding.read(x,y,z); } finally { binding.invalidateProofs(); }
    }
    @Override public boolean canonicalAir(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        if(minX>maxX || minY>maxY || minZ>maxZ) throw new dev.openallay.api.extension.ExtensionException("invalid_bounds","A canonical-air tile must have ordered inclusive bounds");
        validatePosition(minX,minY,minZ); validatePosition(maxX,maxY,maxZ);
        if(Math.floorDiv(minX,16)!=Math.floorDiv(maxX,16) || Math.floorDiv(minZ,16)!=Math.floorDiv(maxZ,16)
                || Math.floorDiv(minY,16)!=Math.floorDiv(maxY,16))
            throw new IllegalArgumentException("Canonical-air proof requires one clipped section");
        return binding.canonicalAir(minX,minY,minZ,maxX,maxY,maxZ);
    }
    @Override public String terrainState(int x,int y,int z) { validatePosition(x,y,z); return binding.terrainState(x,y,z); }
    @Override public int terrainTop(int x,int z,int minY,int maxY) {
        if(minY>=maxY) throw new dev.openallay.api.extension.ExtensionException("invalid_bounds","Terrain height bounds must be a nonempty half-open range");
        validatePosition(x,minY,z);validatePosition(x,maxY-1,z);
        return binding.terrainTop(x,z,minY,maxY);
    }
    @Override public String preview(int x,int y,int z,String state) {
        validatePosition(x,y,z);
        try { return binding.preview(x,y,z,state); } finally { binding.invalidateProofs(); }
    }
    @Override public WriteOutcome write(int x,int y,int z,String state) {
        bridge.checkActive(); validatePosition(x,y,z);
        try { return write(x,y,z,state,binding.read(x,y,z)); }
        finally { binding.invalidateProofs(); }
    }
    @Override public WriteOutcome write(int x,int y,int z,String state,String before) {
        bridge.checkActive();validatePosition(x,y,z);
        try {
            WorldBinding.Image result=binding.write(x,y,z,state,()->{validatePosition(x,y,z);bridge.checkActive();});
            return new WriteOutcome(result.actual(),result.changed(),null);
        } catch(RuntimeException failure) {
            // An admitted mutation must retain its actual outcome even if a native hook or revocation fails.
            try {
                String actual=binding.read(x,y,z);
                return new WriteOutcome(actual,!binding.sameImage(before,actual),OwnerThreadBridge.propagate(failure));
            } catch(RuntimeException unreadable) {
                failure.addSuppressed(unreadable);
                throw OwnerThreadBridge.propagate(failure);
            }
        } finally { binding.invalidateProofs(); }
    }
    @Override public String transform(String state,int degrees,String mirror) { requireOwnerAction(); return binding.transform(state,degrees,mirror); }
    @Override public String repairedState(int x,int y,int z) { RepairOutcome result=repair(x,y,z);return result==null?null:result.intended(); }
    @Override public RepairOutcome repair(int x,int y,int z) {
        bridge.checkActive();validatePosition(x,y,z);
        try { return binding.repair(x,y,z,()->{bridge.checkActive();validatePosition(x,y,z);}); }
        finally { binding.invalidateProofs(); }
    }
    @Override public void notifyNeighbours(int x,int y,int z) {
        bridge.checkActive();validatePosition(x,y,z);
        try { binding.notifyNeighbours(x,y,z,()->{validatePosition(x,y,z);bridge.checkActive();}); }
        finally { binding.invalidateProofs(); }
    }
    @Override public String dimension() { return call(binding::dimension); }
    @Override public String worldId() {
        bridge.checkWorker();bridge.checkActive();
        return call(()->{bridge.checkActive();if(worldId==null)worldId=binding.createWorldId();return worldId;});
    }
    @Override public Optional<String> existingWorldId() {
        bridge.checkWorker();
        return call(()->{if(worldId!=null)return Optional.of(worldId);Optional<String> existing=binding.existingWorldId();existing.ifPresent(id->worldId=id);return existing;});
    }
    @Override public Path artifacts() { return call(binding::artifacts); }
    @Override public boolean isOwnerThread() { return binding.isOwnerThread(); }
    @Override public void close() { bridge.close(); }
}
