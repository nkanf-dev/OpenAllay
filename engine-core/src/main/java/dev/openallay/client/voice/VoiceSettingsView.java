package dev.openallay.client.voice;

import java.util.List;

@dev.openallay.value.ValueType(VoiceSettingsView.ValueSchemaProvider.class)
public final class VoiceSettingsView {
    private final VoiceConfig config;
    private final List<AudioCapture.Device> devices;
    private final boolean busy;
    private final boolean modelReady;
    private final String modelName;
    private final String statusCode;
    private final long downloadedBytes;
    private final long totalBytes;
    public VoiceSettingsView(VoiceConfig config, List<AudioCapture.Device> devices, boolean busy, boolean modelReady, String modelName, String statusCode, long downloadedBytes, long totalBytes) {
 devices = List.copyOf(devices);
        this.config = config;
        this.devices = devices;
        this.busy = busy;
        this.modelReady = modelReady;
        this.modelName = modelName;
        this.statusCode = statusCode;
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
    }
    public VoiceConfig config() { return config; }
    public List<AudioCapture.Device> devices() { return devices; }
    public boolean busy() { return busy; }
    public boolean modelReady() { return modelReady; }
    public String modelName() { return modelName; }
    public String statusCode() { return statusCode; }
    public long downloadedBytes() { return downloadedBytes; }
    public long totalBytes() { return totalBytes; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VoiceSettingsView)) return false;
        VoiceSettingsView that = (VoiceSettingsView) other;
        return java.util.Objects.equals(config, that.config) && java.util.Objects.equals(devices, that.devices) && busy == that.busy && modelReady == that.modelReady && java.util.Objects.equals(modelName, that.modelName) && java.util.Objects.equals(statusCode, that.statusCode) && downloadedBytes == that.downloadedBytes && totalBytes == that.totalBytes;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + java.util.Objects.hashCode(devices);
        hash = 31 * hash + Boolean.hashCode(busy);
        hash = 31 * hash + Boolean.hashCode(modelReady);
        hash = 31 * hash + java.util.Objects.hashCode(modelName);
        hash = 31 * hash + java.util.Objects.hashCode(statusCode);
        hash = 31 * hash + Long.hashCode(downloadedBytes);
        hash = 31 * hash + Long.hashCode(totalBytes);
        return hash;
    }
    @Override public String toString() { return "VoiceSettingsView[config=" + config + ", devices=" + devices + ", busy=" + busy + ", modelReady=" + modelReady + ", modelName=" + modelName + ", statusCode=" + statusCode + ", downloadedBytes=" + downloadedBytes + ", totalBytes=" + totalBytes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<VoiceSettingsView> schema() {
            return new dev.openallay.value.ValueSchema<>(VoiceSettingsView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<VoiceSettingsView>>asList(new dev.openallay.value.ValueSchema.Component<>(VoiceSettingsView.class, "config", VoiceSettingsView::config), new dev.openallay.value.ValueSchema.Component<>(VoiceSettingsView.class, "devices", VoiceSettingsView::devices), new dev.openallay.value.ValueSchema.Component<>(VoiceSettingsView.class, "busy", VoiceSettingsView::busy), new dev.openallay.value.ValueSchema.Component<>(VoiceSettingsView.class, "modelReady", VoiceSettingsView::modelReady), new dev.openallay.value.ValueSchema.Component<>(VoiceSettingsView.class, "modelName", VoiceSettingsView::modelName), new dev.openallay.value.ValueSchema.Component<>(VoiceSettingsView.class, "statusCode", VoiceSettingsView::statusCode), new dev.openallay.value.ValueSchema.Component<>(VoiceSettingsView.class, "downloadedBytes", VoiceSettingsView::downloadedBytes), new dev.openallay.value.ValueSchema.Component<>(VoiceSettingsView.class, "totalBytes", VoiceSettingsView::totalBytes)), arguments -> new VoiceSettingsView((VoiceConfig) arguments[0], (List) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (String) arguments[4], (String) arguments[5], (Long) arguments[6], (Long) arguments[7]));
        }
    }
}
