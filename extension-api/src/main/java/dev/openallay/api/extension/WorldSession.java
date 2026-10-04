package dev.openallay.api.extension;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Typed native-operation port. Canonical state JSON is detached and may contain opaque native
 * block-entity payloads; terrainState omits those payloads. No game handle crosses this boundary.
 * The adapter checks scope, capability and exact bound session immediately before owner actions.
 */
public interface WorldSession extends AutoCloseable {
    /** Trusted Java owner work only, never a script/Agent callback. Core detaches script results. */
    <T> T call(Callable<T> ownerAction);
    long sliceDeadline();
    long dispatches();
    boolean isOwnerThread();
    void validatePosition(int x, int y, int z);
    String read(int x, int y, int z);
    /** Null only for exact canonical air; custom/cave/void air is not silently omitted. */
    String readNonAir(int x, int y, int z);
    /** Exact palette proof for one clipped section tile, valid only within this owner action. */
    boolean canonicalAir(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    String terrainState(int x, int y, int z);
    /** minY inclusive, maxY exclusive; returns an inclusive safe scan start. */
    int terrainTop(int x, int z, int minY, int maxY);
    String preview(int x, int y, int z, String stateJson);
    WriteOutcome write(int x, int y, int z, String stateJson);
    WriteOutcome write(int x, int y, int z, String stateJson, String beforeJson);
    String transform(String stateJson, int degrees, String mirror);
    /** Null if no repair is needed. */
    String repairedState(int x, int y, int z);
    /** Null if no repair is needed. */
    RepairOutcome repair(int x, int y, int z);
    void notifyNeighbours(int x, int y, int z);
    String context();
    String dimension();
    /** May create native saved identity only during an authorized world action. */
    String worldId();
    /** Reads without creating native saved identity. */
    Optional<String> existingWorldId();
    Path artifacts();
    /** Revokes this session's lifetime; repeated close must be harmless. */
    @Override void close();

    /** Detached write accounting. A failure may follow a changed write and must not erase that fact. */
    final class WriteOutcome {
        private final String actual;
        private final boolean changed;
        private final RuntimeException failure;
        public WriteOutcome(String actual, boolean changed, RuntimeException failure) {
            // Actual can be unavailable on failed readback; preserve the backend's accounting.
            if (actual != null) ApiValidation.text(actual, "actual state JSON");
            this.actual = actual; this.changed = changed; this.failure = failure;
        }
        public String actual() { return actual; }
        public boolean changed() { return changed; }
        public RuntimeException failure() { return failure; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof WriteOutcome)) return false;
            WriteOutcome that = (WriteOutcome) other;
            return changed == that.changed && Objects.equals(actual, that.actual) && Objects.equals(failure, that.failure);
        }
        @Override public int hashCode() { return Objects.hash(actual, changed, failure); }
    }

    final class RepairOutcome {
        private final String before;
        private final String intended;
        public RepairOutcome(String before, String intended) {
            this.before = ApiValidation.text(before, "before state JSON");
            this.intended = ApiValidation.text(intended, "intended state JSON");
        }
        public String before() { return before; }
        public String intended() { return intended; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RepairOutcome)) return false;
            RepairOutcome that = (RepairOutcome) other;
            return before.equals(that.before) && intended.equals(that.intended);
        }
        @Override public int hashCode() { return Objects.hash(before, intended); }
    }
}
