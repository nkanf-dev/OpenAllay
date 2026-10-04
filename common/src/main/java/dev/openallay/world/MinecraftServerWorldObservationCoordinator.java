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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Server-authoritative spatial capture scoped to one authenticated player request.
 *
 * <p>Every Minecraft object is read only on the server thread and immediately projected into
 * immutable OpenAllay records. The fixed work quantum controls yielding, not result size.
 */
public final class MinecraftServerWorldObservationCoordinator
        implements WorldObservationCoordinator {
    private static final int POSITIONS_PER_SLICE = 2_048;

    private final MinecraftServer server;
    private final PlatformService platform;
    private final UUID expectedActor;
    private final String expectedDimension;
    private final String observationPrefix = UUID.randomUUID().toString();
    private final AtomicLong entitySequence = new AtomicLong();
    private final Map<String, WorldEntitySnapshot> entityDetails = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public MinecraftServerWorldObservationCoordinator(
            MinecraftServer server,
            PlatformService platform,
            UUID expectedActor,
            String expectedDimension) {
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.expectedActor = java.util.Objects.requireNonNull(expectedActor, "expectedActor");
        if (expectedDimension == null || expectedDimension.isBlank()) {
            throw new IllegalArgumentException("expectedDimension must not be blank");
        }
        this.expectedDimension = expectedDimension;
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
                ServerPlayer player = verifyAvailable(cancellation);
                var level = player.level();
                WorldBounds bounds = request.bounds();
                AABB box = new AABB(
                        bounds.from().x(),
                        bounds.from().y(),
                        bounds.from().z(),
                        (double) bounds.to().x() + 1,
                        (double) bounds.to().y() + 1,
                        (double) bounds.to().z() + 1);
                List<Entity> captured = level.getEntities(
                        (Entity) null,
                        box,
                        entity -> request.entityType().isEmpty()
                                || request.entityType().equals(entityType(entity)));
                WorldObservationCoverage coverage =
                        WorldObservationCoverageCalculator.calculate(
                                bounds,
                                level.getMinY(),
                                level.getMaxY(),
                                (chunkX, chunkZ) -> level.hasChunkAt(new BlockPos(
                                        chunkX << 4,
                                        Math.max(bounds.from().y(), level.getMinY()),
                                        chunkZ << 4)));
                ArrayList<WorldEntitySummary> summaries = new ArrayList<>(captured.size());
                for (Entity entity : captured) {
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
                            entity.isAlive()));
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
            return CompletableFuture.failedFuture(cancelled());
        }
        WorldEntitySnapshot snapshot = entityDetails.get(observationId);
        if (snapshot == null) {
            return CompletableFuture.failedFuture(new JavascriptExecutionException(
                    "world_entity_unavailable",
                    "Entity observation ID is unavailable in this request"));
        }
        return CompletableFuture.completedFuture(snapshot);
    }

    @Override
    public void close() {
        closed.set(true);
        entityDetails.clear();
    }

    private void captureBlockSlice(BlockCapture capture) {
        try {
            ServerPlayer player = verifyAvailable(capture.cancellation);
            var level = player.level();
            WorldBounds bounds = capture.request.bounds();
            long volume = bounds.volume();
            int processed = 0;
            while (capture.index < volume && processed < POSITIONS_PER_SLICE) {
                capture.cancellation.throwIfCancelled();
                WorldPosition position = position(bounds, capture.index++);
                BlockPos blockPos = new BlockPos(position.x(), position.y(), position.z());
                if (level.isOutsideBuildHeight(blockPos)) {
                    capture.unavailable.add("height:" + position.y());
                    processed++;
                    continue;
                }
                if (!level.hasChunkAt(blockPos)) {
                    capture.unavailable.add(
                            "chunk:" + (position.x() >> 4) + "," + (position.z() >> 4));
                    processed++;
                    continue;
                }
                capture.loaded++;
                var state = level.getBlockState(blockPos);
                if (capture.request.includeAir() || !state.isAir()) {
                    LinkedHashMap<String, String> properties = new LinkedHashMap<>();
                    properties.putAll(dev.openallay.context.minecraft.MinecraftBlockStateProperties.capture(state));
                    var fluidState = state.getFluidState();
                    String fluid = fluidState.isEmpty()
                            ? ""
                            : BuiltInRegistries.FLUID.getKey(fluidState.getType()).toString();
                    capture.blocks.add(new WorldBlockSnapshot(
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                            position,
                            position.subtract(bounds.from()),
                            properties,
                            fluid,
                            level.getBlockEntity(blockPos) != null));
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
                            List.copyOf(capture.unavailable)),
                    evidence(
                            complete ? DataCompleteness.COMPLETE : DataCompleteness.PARTIAL,
                            "minecraft:server_blocks")));
        } catch (RuntimeException failure) {
            capture.result.completeExceptionally(translate(failure));
        }
    }

    private WorldEntitySnapshot detail(String observationId, Entity entity) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("x", entity.position().x());
        data.put("y", entity.position().y());
        data.put("z", entity.position().z());
        data.put("velocityX", entity.getDeltaMovement().x());
        data.put("velocityY", entity.getDeltaMovement().y());
        data.put("velocityZ", entity.getDeltaMovement().z());
        data.put("pose", entity.getPose().name().toLowerCase(java.util.Locale.ROOT));
        data.put("width", entity.getBbWidth());
        data.put("height", entity.getBbHeight());
        data.put("alive", entity.isAlive());
        if (entity instanceof LivingEntity living) {
            data.put("health", living.getHealth());
            data.put("maxHealth", living.getMaxHealth());
            data.put("armor", living.getArmorValue());
            data.put("effects", living.getActiveEffects().stream()
                    .map(effect -> effect.getEffect().unwrapKey()
                            .map(key -> key.identifier().toString())
                            .orElse("unknown"))
                    .sorted()
                    .toList());
        }
        BlockPos position = entity.blockPosition();
        return new WorldEntitySnapshot(
                observationId,
                entity.getUUID(),
                entityType(entity),
                entity.getName().getString(),
                new WorldPosition(position.getX(), position.getY(), position.getZ()),
                data,
                evidence(DataCompleteness.COMPLETE, "minecraft:server_entity"));
    }

    private void schedule(Runnable action) {
        if (closed.get()) {
            throw cancelled();
        }
        server.execute(action);
    }

    private ServerPlayer verifyAvailable(CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        if (closed.get()) {
            throw cancelled();
        }
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "World observation must run on the Minecraft server thread");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(expectedActor);
        if (player == null) {
            throw cancelled();
        }
        if (!expectedDimension.equals(
                player.level().dimension().identifier().toString())) {
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

    private static String entityType(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
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
