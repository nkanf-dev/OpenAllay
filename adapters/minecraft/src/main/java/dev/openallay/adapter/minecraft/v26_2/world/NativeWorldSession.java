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
    // Owner-local, opt-in facts only. Never retain an unbounded native state or Throwable.
    private com.google.gson.JsonObject nativeDiagnosticFacts;
    private int nativeDiagnosticReports;
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
            catch(Exception | Error failure) { reportNativeDiagnostic(failure); throw failure; }
            finally { ownerAction=false; nativeDiagnosticFacts=null; binding.endAction(); }
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
        rememberNativeDiagnostic("preview",x,y,z,true,state,0,null);
        validatePosition(x,y,z);
        try { return binding.preview(x,y,z,state); } finally { binding.invalidateProofs(); }
    }
    @Override public WriteOutcome write(int x,int y,int z,String state) {
        bridge.checkActive(); validatePosition(x,y,z);
        try { return write(x,y,z,state,binding.read(x,y,z)); }
        finally { binding.invalidateProofs(); }
    }
    @Override public WriteOutcome write(int x,int y,int z,String state,String before) {
        rememberNativeDiagnostic("write",x,y,z,true,state,0,null);
        bridge.checkActive();validatePosition(x,y,z);
        try {
            WorldBinding.Image result=binding.write(x,y,z,state,()->{validatePosition(x,y,z);bridge.checkActive();});
            return new WriteOutcome(result.actual(),result.changed(),null);
        } catch(RuntimeException failure) {
            reportNativeDiagnostic(failure); // Capture the original before player-safe conversion.
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
    @Override public String transform(String state,int degrees,String mirror) {
        rememberNativeDiagnostic("transform",0,0,0,false,state,degrees,mirror);
        requireOwnerAction(); return binding.transform(state,degrees,mirror);
    }
    @Override public String repairedState(int x,int y,int z) { RepairOutcome result=repair(x,y,z);return result==null?null:result.intended(); }
    @Override public RepairOutcome repair(int x,int y,int z) {
        rememberNativeDiagnostic("repair",x,y,z,true,null,0,null);
        bridge.checkActive();validatePosition(x,y,z);
        try { return binding.repair(x,y,z,()->{bridge.checkActive();validatePosition(x,y,z);}); }
        finally { binding.invalidateProofs(); }
    }
    @Override public void notifyNeighbours(int x,int y,int z) {
        rememberNativeDiagnostic("notifyNeighbours",x,y,z,true,null,0,null);
        bridge.checkActive();validatePosition(x,y,z);
        try { binding.notifyNeighbours(x,y,z,()->{validatePosition(x,y,z);bridge.checkActive();}); }
        finally { binding.invalidateProofs(); }
    }
    private static boolean nativeDiagnosticEnabled() {
        return Boolean.getBoolean("openallay.e2e.enabled")
                && Boolean.getBoolean("openallay.e2e.worldNativeDiagnostic");
    }
    private void rememberNativeDiagnostic(String operation,int x,int y,int z,boolean positioned,
            String state,int degrees,String mirror) {
        if(!nativeDiagnosticEnabled())return;
        com.google.gson.JsonObject facts=new com.google.gson.JsonObject();
        facts.addProperty("operation",operation);
        if(positioned) { facts.addProperty("x",x);facts.addProperty("y",y);facts.addProperty("z",z); }
        if(state!=null) { facts.addProperty("input",boundedDiagnosticText(state,1024));facts.addProperty("inputChars",state.length()); }
        if("transform".equals(operation)) {
            facts.addProperty("degrees",degrees);
            facts.addProperty("mirror",boundedDiagnosticText(mirror,64));
        }
        nativeDiagnosticFacts=facts;
    }
    private static String boundedDiagnosticText(String value,int limit) {
        return value==null || value.length()<=limit ? value : value.substring(0,limit);
    }
    private void reportNativeDiagnostic(Throwable original) {
        if(!nativeDiagnosticEnabled() || nativeDiagnosticReports>=4)return;
        nativeDiagnosticReports++;
        try {
            com.google.gson.JsonObject receipt=new com.google.gson.JsonObject();
            receipt.addProperty("kind","openallay_native_world_diagnostic");
            receipt.addProperty("sequence",nativeDiagnosticReports);
            if(nativeDiagnosticFacts!=null)receipt.add("lastNativeOperation",nativeDiagnosticFacts);
            com.google.gson.JsonArray causes=new com.google.gson.JsonArray();
            java.util.Set<Throwable> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            Throwable cause=original;
            for(int count=0;cause!=null && count<16 && seen.add(cause);count++,cause=cause.getCause()) {
                com.google.gson.JsonObject item=new com.google.gson.JsonObject();
                item.addProperty("class",cause.getClass().getName());
                String message=cause.getMessage();
                item.addProperty("message",boundedDiagnosticText(message,1024));
                item.addProperty("messageChars",message==null?0:message.length());
                com.google.gson.JsonArray frames=new com.google.gson.JsonArray();
                StackTraceElement[] stack=cause.getStackTrace();
                for(int index=0;index<Math.min(stack.length,12);index++)
                    frames.add(boundedDiagnosticText(stack[index].toString(),512));
                item.add("stack",frames);item.addProperty("stackFrames",stack.length);
                causes.add(item);
            }
            receipt.add("causes",causes);
            receipt.addProperty("causeChainTruncated",cause!=null);
            System.err.println("OpenAllay native world diagnostic: "+receipt);
        } catch(Throwable diagnosticFailure) {
            // Diagnostics must never replace, suppress or mutate the original failure.
        }
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
