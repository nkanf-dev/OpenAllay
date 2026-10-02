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
        List<Device> devices();
    }

    record Device(String id, String name) {}
}
