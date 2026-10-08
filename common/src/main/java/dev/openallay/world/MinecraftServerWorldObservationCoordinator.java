package dev.openallay.world;

import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.platform.PlatformService;
import dev.openallay.script.JavascriptExecutionException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;






/**
 * Server-authoritative spatial capture scoped to one authenticated player request.
 *
 * <p>Every Minecraft object is read only on the server thread and immediately projected into
 * immutable OpenAllay records. The fixed work quantum controls yielding, not result size.
 */
public final class MinecraftServerWorldObservationCoordinator
        implements WorldObservationCoordinator {
    private static final int POSITIONS_PER_SLICE = 2_048;

    private final net.minecraft.server.MinecraftServer server;
    private final PlatformService platform;
    private final UUID expectedActor;
    private final net.minecraft.server.level.ServerPlayer expectedPlayer;
    private final net.minecraft.server.level.ServerLevel expectedLevel;
    private final java.util.function.BooleanSupplier connectionCurrent;
    private final OwnerDispatch dispatch;
    @FunctionalInterface public interface OwnerDispatch {
        boolean dispatch(Runnable action, Runnable retired);
    }
    private final String expectedDimension;
    private final String observationPrefix = UUID.randomUUID().toString();
    private final AtomicLong entitySequence = new AtomicLong();
    private final Map<String, WorldEntitySnapshot> entityDetails = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public MinecraftServerWorldObservationCoordinator(net.minecraft.server.MinecraftServer server, PlatformService platform,
            UUID expectedActor, String expectedDimension, net.minecraft.server.level.ServerPlayer expectedPlayer,
            net.minecraft.server.level.ServerLevel expectedLevel,
            java.util.function.BooleanSupplier connectionCurrent, OwnerDispatch dispatch) {
        this.expectedPlayer = expectedPlayer; this.expectedLevel = expectedLevel;
        this.connectionCurrent = connectionCurrent; this.dispatch = dispatch;
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.expectedActor = java.util.Objects.requireNonNull(expectedActor, "expectedActor");
        if (expectedDimension == null || dev.openallay.util.Java8Strings.isBlank(expectedDimension)) {
            throw new IllegalArgumentException("expectedDimension must not be blank");
        }
        this.expectedDimension = expectedDimension;
    }

    @Override
    public CompletionStage<BlockObservation> inspect(
            WorldObservationRequest request, CancellationSignal cancellation) {
        java.util.Objects.requireNonNull(request, "request");
        BlockCapture capture = new BlockCapture(request, cancellation);
        schedule(() -> captureBlockSlice(capture), capture.result);
        return capture.result;
    }

    @Override
    public CompletionStage<EntityObservation> entities(
            WorldObservationRequest request, CancellationSignal cancellation) {
        java.util.Objects.requireNonNull(request, "request");
        CompletableFuture<EntityObservation> result = new CompletableFuture<>();
        schedule(() -> {
            try {
                net.minecraft.server.level.ServerPlayer player = verifyAvailable(cancellation);
                net.minecraft.server.level.ServerLevel level = dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player);
                WorldBounds bounds = request.bounds();
                net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                        bounds.from().x(),
                        bounds.from().y(),
                        bounds.from().z(),
                        (double) bounds.to().x() + 1,
                        (double) bounds.to().y() + 1,
                        (double) bounds.to().z() + 1);
                List<net.minecraft.world.entity.Entity> captured = MinecraftWorldObservationFacts.entities(level,
                        box,
                        entity -> request.entityType().isEmpty()
                                || request.entityType().equals(entityType(entity)));
                WorldObservationCoverage coverage =
                        WorldObservationCoverageCalculator.calculate(
                                bounds,
                                dev.openallay.context.minecraft.MinecraftWorldHeight.min(level),
                                dev.openallay.context.minecraft.MinecraftWorldHeight.max(level),
                                (chunkX, chunkZ) -> MinecraftWorldObservationFacts.loaded(level,new net.minecraft.core.BlockPos(
                                        chunkX << 4,
                                        Math.max(bounds.from().y(), dev.openallay.context.minecraft.MinecraftWorldHeight.min(level)),
                                        chunkZ << 4)));
                ArrayList<WorldEntitySummary> summaries = new ArrayList<>(captured.size());
                for (net.minecraft.world.entity.Entity entity : captured) {
                    cancellation.throwIfCancelled();
                    String observationId =
                            observationPrefix + "-" + entitySequence.incrementAndGet();
                    WorldEntitySnapshot detail = detail(observationId, entity);
                    entityDetails.put(observationId, detail);
                    summaries.add(new WorldEntitySummary(
                            observationId,
                            detail.type(),
                            detail.name(),
                            detail.position(),
                            dev.openallay.client.context.MinecraftClientContextFacts.alive(entity)));
                }
                summaries.sort(java.util.Comparator.comparing(WorldEntitySummary::observationId));
                result.complete(new EntityObservation(
                        bounds,
                        summaries,
                        coverage,
                        evidence(
                                coverage.complete()
                                        ? DataCompleteness.COMPLETE
                                        : DataCompleteness.PARTIAL,
                                "minecraft:server_entities")));
            } catch (RuntimeException failure) { result.completeExceptionally(translate(failure)); }
            catch (Error failure) { result.completeExceptionally(failure); throw failure; }
        }, result);
        return result;
    }

    @Override
    public CompletionStage<WorldEntitySnapshot> entity(
            String observationId, CancellationSignal cancellation) {
        CompletableFuture<WorldEntitySnapshot> result = new CompletableFuture<>();
        schedule(() -> {
            try {
                verifyAvailable(cancellation);
                WorldEntitySnapshot snapshot = entityDetails.get(observationId);
                if (snapshot == null) throw new JavascriptExecutionException(
                        "world_entity_unavailable", "Entity observation ID is unavailable in this request");
                result.complete(snapshot);
            } catch (RuntimeException failure) { result.completeExceptionally(translate(failure)); }
            catch (Error failure) { result.completeExceptionally(failure); throw failure; }
        }, result);
        return result;
    }

    @Override
    public void close() {
        closed.set(true);
        entityDetails.clear();
    }

    private void captureBlockSlice(BlockCapture capture) {
        try {
            net.minecraft.server.level.ServerPlayer player = verifyAvailable(capture.cancellation);
            net.minecraft.server.level.ServerLevel level = dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player);
            WorldBounds bounds = capture.request.bounds();
            long volume = bounds.volume();
            int processed = 0;
            while (capture.index < volume && processed < POSITIONS_PER_SLICE) {
                capture.cancellation.throwIfCancelled();
                WorldPosition position = position(bounds, capture.index++);
                net.minecraft.core.BlockPos blockPos = new net.minecraft.core.BlockPos(position.x(), position.y(), position.z());
                if (level.isOutsideBuildHeight(blockPos)) {
                    capture.unavailable.add("height:" + position.y());
                    processed++;
                    continue;
                }
                if (!MinecraftWorldObservationFacts.loaded(level,blockPos)) {
                    capture.unavailable.add(
                            "chunk:" + (position.x() >> 4) + "," + (position.z() >> 4));
                    processed++;
                    continue;
                }
                capture.loaded++;
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(blockPos);
                if (capture.request.includeAir() || !MinecraftWorldObservationFacts.air(level,state,blockPos)) {
                    LinkedHashMap<String, String> properties = new LinkedHashMap<>();
                    properties.putAll(dev.openallay.context.minecraft.MinecraftBlockStateProperties.capture(state));
                    String fluid=dev.openallay.client.observation.MinecraftHitFacts.fluid(state);
                    capture.blocks.add(new WorldBlockSnapshot(
                            MinecraftNativeRegistries.BLOCK.getKey(state.getBlock()).toString(),
                            position,
                            position.subtract(bounds.from()),
                            properties,
                            fluid,
                            MinecraftWorldObservationFacts.hasBlockEntity(level,blockPos)));
                }
                processed++;
            }
            if (capture.index < volume) {
                schedule(() -> captureBlockSlice(capture), capture.result);
                return;
            }
            boolean complete = capture.loaded == volume;
            capture.result.complete(new BlockObservation(
                    bounds,
                    capture.blocks,
                    new WorldObservationCoverage(
                            volume,
                            capture.loaded,
                            complete,
                            dev.openallay.util.Java8Collections.listCopyOf(capture.unavailable)),
                    evidence(
                            complete ? DataCompleteness.COMPLETE : DataCompleteness.PARTIAL,
                            "minecraft:server_blocks")));
        } catch (RuntimeException failure) { capture.result.completeExceptionally(translate(failure)); }
        catch (Error failure) { capture.result.completeExceptionally(failure); throw failure; }
    }

    private WorldEntitySnapshot detail(String observationId, net.minecraft.world.entity.Entity entity) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("x", dev.openallay.client.context.MinecraftClientContextFacts.x(entity));
        data.put("y", dev.openallay.client.context.MinecraftClientContextFacts.y(entity));
        data.put("z", dev.openallay.client.context.MinecraftClientContextFacts.z(entity));
        data.put("velocityX", MinecraftWorldObservationFacts.motionX(entity));
        data.put("velocityY", MinecraftWorldObservationFacts.motionY(entity));
        data.put("velocityZ", MinecraftWorldObservationFacts.motionZ(entity));
        String pose=MinecraftWorldObservationFacts.pose(entity);
        if(pose!=null)data.put("pose",pose);
        data.put("width", MinecraftWorldObservationFacts.width(entity));
        data.put("height", MinecraftWorldObservationFacts.height(entity));
        data.put("alive", dev.openallay.client.context.MinecraftClientContextFacts.alive(entity));
        final class $oaPattern0_Holder { net.minecraft.world.entity.Entity value; net.minecraft.world.entity.LivingEntity bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = entity) instanceof net.minecraft.world.entity.LivingEntity && (($oaPattern0_holder.bound = (net.minecraft.world.entity.LivingEntity) $oaPattern0_holder.value) != null))) {
            data.put("health", $oaPattern0_holder.bound.getHealth());
            data.put("maxHealth", $oaPattern0_holder.bound.getMaxHealth());
            data.put("armor", MinecraftWorldObservationFacts.armor($oaPattern0_holder.bound));
            data.put("effects", dev.openallay.util.Java8Collections.toList(MinecraftWorldObservationFacts.effects($oaPattern0_holder.bound).stream()
                    .map(dev.openallay.context.minecraft.MinecraftActiveEffectFacts::id)
                    .sorted()));
        }
        net.minecraft.core.BlockPos position = dev.openallay.client.context.MinecraftClientContextFacts.position(entity);
        return new WorldEntitySnapshot(
                observationId,
                dev.openallay.client.context.MinecraftClientContextFacts.uuid(entity),
                entityType(entity),
                dev.openallay.client.context.MinecraftClientContextFacts.name(entity),
                new WorldPosition(position.getX(), position.getY(), position.getZ()),
                data,
                evidence(DataCompleteness.COMPLETE, "minecraft:server_entity"));
    }

    private void schedule(Runnable action, CompletableFuture<?> result) {
        if (closed.get()) { result.completeExceptionally(cancelled()); return; }
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            dispatch.dispatch(() -> { ran.set(true); action.run(); }, () -> {
                if (!ran.get() && !result.isDone()) result.completeExceptionally(cancelled());
            });
        } catch (RuntimeException failure) { result.completeExceptionally(translate(failure)); }
        catch (Error failure) { result.completeExceptionally(failure); throw failure; }
    }

    private net.minecraft.server.level.ServerPlayer verifyAvailable(CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        if (closed.get()) {
            throw cancelled();
        }
        if (!dev.openallay.server.NativeServerOwner.isOwner(server)) {
            throw new IllegalStateException(
                    "World observation must run on the Minecraft server thread");
        }
        net.minecraft.server.level.ServerPlayer player = dev.openallay.server.NativeServerOwner.player(server,expectedActor);
        if (player != expectedPlayer || !connectionCurrent.getAsBoolean()
                || dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player) != expectedLevel) {
            throw cancelled();
        }
        if (!expectedDimension.equals(
                MinecraftWorldObservationFacts.dimension(dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player)))) {
            throw new JavascriptExecutionException(
                    "world_observation_unavailable",
                    "Player changed dimension during world observation");
        }
        return player;
    }

    private EvidenceMetadata evidence(DataCompleteness completeness, String source) {
        return WorldObservationEvidence.server(
                platform, completeness, Instant.now(), source, expectedDimension);
    }

    private static WorldPosition position(WorldBounds bounds, long index) {
        long sizeY = (long) bounds.to().y() - bounds.from().y() + 1;
        long sizeZ = (long) bounds.to().z() - bounds.from().z() + 1;
        long yz = Math.multiplyExact(sizeY, sizeZ);
        int x = Math.toIntExact(bounds.from().x() + index / yz);
        long remainder = index % yz;
        int y = Math.toIntExact(bounds.from().y() + remainder / sizeZ);
        int z = Math.toIntExact(bounds.from().z() + remainder % sizeZ);
        return new WorldPosition(x, y, z);
    }

    private static String entityType(net.minecraft.world.entity.Entity entity) {
        return dev.openallay.client.observation.MinecraftHitFacts.entityType(entity);
    }

    private static RuntimeException translate(RuntimeException failure) {
        if (failure instanceof JavascriptExecutionException
                || failure instanceof dev.openallay.model.ModelClientException) {
            return failure;
        }
        return new JavascriptExecutionException(
                "world_observation_failed",
                "World observation failed",
                failure);
    }

    private static JavascriptExecutionException cancelled() {
        return new JavascriptExecutionException(
                "world_observation_cancelled",
                "World observation is no longer available for this request");
    }

    private static final class BlockCapture {
        private final WorldObservationRequest request;
        private final CancellationSignal cancellation;
        private final CompletableFuture<BlockObservation> result = new CompletableFuture<>();
        private final ArrayList<WorldBlockSnapshot> blocks = new ArrayList<>();
        private final LinkedHashSet<String> unavailable = new LinkedHashSet<>();
        private long index;
        private long loaded;

        private BlockCapture(
                WorldObservationRequest request, CancellationSignal cancellation) {
            this.request = request;
            this.cancellation = cancellation;
        }
    }
}
