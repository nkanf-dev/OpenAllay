package dev.openallay.client.voice;

import java.util.List;

/** A worker-owned stream of 16 kHz mono signed 16-bit little-endian PCM. */
public interface AudioCapture extends AutoCloseable {
    /** Reads currently available whole frames, returns 0 if none, or -1 after close. */
    int read(byte[] buffer) throws Exception;

    /** Stops capture and releases the device. Repeated calls have no effect. */
    @Override
    void close();

    interface Factory {
        /** Opens only in response to an explicit recording action, on a worker. */
        default AudioCapture open(String deviceId) throws Exception {
            return open(deviceId, new VoiceCancellation());
        }

        /** The open owner must fence late completion and release a line selected before cancellation. */
        AudioCapture open(String deviceId, VoiceCancellation cancellation) throws Exception;

        /** Enumerates devices only for an explicit device-list refresh, on a worker. */
        List<Device> devices() throws CaptureException;
    }

    enum Failure {
        DEVICE_UNAVAILABLE, BACKEND_UNAVAILABLE, UNSUPPORTED_FORMAT, OPEN_FAILED,
        OPEN_TIMEOUT, OPEN_BUSY, READ_FAILED, DEVICE_DISCONNECTED
    }

    /** Backend-neutral failure. Provider messages never become player-facing status text. */
    final class CaptureException extends Exception {
        private final Failure failure;

        CaptureException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        CaptureException(Failure failure, String message, Throwable cause) {
            super(message, cause);
            this.failure = failure;
        }

        public Failure failure() { return failure; }
    }

    @dev.openallay.value.ValueType(Device.ValueSchemaProvider.class)
public static final class Device {
    private final String id;
    private final String name;
    public Device(String id, String name) {
        this.id = id;
        this.name = name;
    }
    public String id() { return id; }
    public String name() { return name; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Device)) return false;
        Device that = (Device) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        return hash;
    }
    @Override public String toString() { return "Device[id=" + id + ", name=" + name + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Device> schema() {
            return new dev.openallay.value.ValueSchema<>(Device.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Device>>asList(new dev.openallay.value.ValueSchema.Component<>(Device.class, "id", Device::id), new dev.openallay.value.ValueSchema.Component<>(Device.class, "name", Device::name)), arguments -> new Device((String) arguments[0], (String) arguments[1]));
        }
    }
}
}
