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
 * Client-visible spatial capture. Each scheduled block slice detaches at most one fixed work
 * quantum and then yields back to the client executor; the quantum is not a result cap.
 */
public final class MinecraftClientWorldObservationCoordinator
        implements WorldObservationCoordinator {
    private static final int POSITIONS_PER_SLICE = 2_048;

    private final net.minecraft.client.Minecraft client;
    private final PlatformService platform;
    private final UUID expectedActor;
    private final String expectedDimension;
    private final Object expectedLevel;
    private final dev.openallay.client.observation.MinecraftClientViewCapture views;
    private final String observationPrefix = UUID.randomUUID().toString();
    private final AtomicLong entitySequence = new AtomicLong();
    private final Map<String, WorldEntitySnapshot> entityDetails = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.Set<CompletableFuture<WorldFocusObservation>> focusRequests =
            ConcurrentHashMap.newKeySet();

    public MinecraftClientWorldObservationCoordinator(
            net.minecraft.client.Minecraft client,
            PlatformService platform,
            UUID expectedActor,
            String expectedDimension) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.expectedActor = java.util.Objects.requireNonNull(expectedActor, "expectedActor");
        if (expectedDimension == null || dev.openallay.util.Java8Strings.isBlank(expectedDimension)) {
            throw new IllegalArgumentException("expectedDimension must not be blank");
        }
        this.expectedDimension = expectedDimension;
        this.expectedLevel = MinecraftWorldObservationFacts.clientLevel(client);
        this.views = null;
    }

    public MinecraftClientWorldObservationCoordinator(
            net.minecraft.client.Minecraft client, PlatformService platform, UUID expectedActor, String expectedDimension,
            WorldObservationRuntime observations, String correlationId) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.expectedActor = java.util.Objects.requireNonNull(expectedActor, "expectedActor");
        this.expectedDimension = java.util.Objects.requireNonNull(expectedDimension, "expectedDimension");
        this.expectedLevel = MinecraftWorldObservationFacts.clientLevel(client);
        this.views = new dev.openallay.client.observation.MinecraftClientViewCapture(
                client, platform, observations, correlationId, expectedActor, expectedDimension);
    }

    @Override
    public CompletionStage<WorldFocusObservation> focus(CancellationSignal cancellation) {
        CompletableFuture<WorldFocusObservation> result = new CompletableFuture<>();
        focusRequests.add(result);
        result.whenComplete((value, failure) -> focusRequests.remove(result));
        cancellation.onCancel(() -> result.completeExceptionally(unavailable()));
        try {
            schedule(() -> {
                try {
                    verifyAvailable(cancellation);
                    result.complete(dev.openallay.client.context.ClientFocusCapture.capture(client, platform));
                } catch (RuntimeException failure) { result.completeExceptionally(translate(failure)); }
            });
        } catch (RuntimeException failure) { result.completeExceptionally(translate(failure)); }
        return result;
    }

    @Override
    public CompletionStage<WorldViewCapture> capture(WorldViewRequest request, CancellationSignal cancellation) {
        if (views == null) return WorldObservationCoordinator.super.capture(request, cancellation);
        return views.capture(request, cancellation);
    }

    @Override
    public CompletionStage<BlockObservation> inspect(
            WorldObservationRequest request, CancellationSignal cancellation) {
        java.util.Objects.requireNonNull(request, "request");
        BlockCapture capture = new BlockCapture(request, cancellation);
        schedule(() -> captureBlockSlice(capture));
        return capture.result;
    }

    @Override
    public CompletionStage<EntityObservation> entities(
            WorldObservationRequest request, CancellationSignal cancellation) {
        java.util.Objects.requireNonNull(request, "request");
        CompletableFuture<EntityObservation> result = new CompletableFuture<>();
        schedule(() -> {
            try {
                verifyAvailable(cancellation);
                net.minecraft.world.level.Level level = MinecraftWorldObservationFacts.clientLevel(client);
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
                        evidence(DataCompleteness.PARTIAL, "minecraft:client_entities")));
            } catch (RuntimeException failure) {
                result.completeExceptionally(translate(failure));
            }
        });
        return result;
    }

    @Override
    public CompletionStage<WorldEntitySnapshot> entity(
            String observationId, CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        if (closed.get()) {
            return dev.openallay.util.Java8Futures.failedFuture(unavailable());
        }
        WorldEntitySnapshot snapshot = entityDetails.get(observationId);
        if (snapshot == null) {
            return dev.openallay.util.Java8Futures.failedFuture(new JavascriptExecutionException(
                    "world_entity_unavailable",
                    "Entity observation ID is unavailable in this request"));
        }
        return CompletableFuture.completedFuture(snapshot);
    }

    @Override
    public void close() {
        closed.set(true);
        if (views != null) views.close();
        focusRequests.forEach(result -> result.completeExceptionally(unavailable()));
        focusRequests.clear();
        entityDetails.clear();
    }

    private void captureBlockSlice(BlockCapture capture) {
        try {
            verifyAvailable(capture.cancellation);
            WorldBounds bounds = capture.request.bounds();
            long volume = bounds.volume();
            int processed = 0;
            net.minecraft.world.level.Level level = MinecraftWorldObservationFacts.clientLevel(client);
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
                schedule(() -> captureBlockSlice(capture));
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
                            "minecraft:client_blocks")));
        } catch (RuntimeException failure) {
            capture.result.completeExceptionally(translate(failure));
        }
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
                evidence(DataCompleteness.PARTIAL, "minecraft:client_entity"));
    }

    private void schedule(Runnable action) {
        if (closed.get()) {
            throw unavailable();
        }
        dev.openallay.client.context.MinecraftClientContextFacts.execute(client,action);
    }

    private void verifyAvailable(CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        if (closed.get()
                || MinecraftWorldObservationFacts.clientLevel(client) != expectedLevel
                || client.player == null
                || MinecraftWorldObservationFacts.clientLevel(client) == null
                || !expectedActor.equals(dev.openallay.client.context.MinecraftClientContextFacts.uuid(client.player))
                || !expectedDimension.equals(
                        MinecraftWorldObservationFacts.dimension(MinecraftWorldObservationFacts.clientLevel(client)))) {
            throw unavailable();
        }
        if (!dev.openallay.client.context.MinecraftClientContextFacts.ownerThread(client)) {
            throw new IllegalStateException(
                    "World observation must run on the Minecraft client thread");
        }
    }

    private EvidenceMetadata evidence(DataCompleteness completeness, String source) {
        return WorldObservationEvidence.client(
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

    private static JavascriptExecutionException unavailable() {
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
