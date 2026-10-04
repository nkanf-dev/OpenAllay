package dev.openallay.api.extension;

import java.util.Objects;

/** One declared compatibility coordinate; targets form explicit unions, not a tested-support claim. */
public final class SupportTarget {
    private final String loader;
    private final String minecraftVersionRange;
    private final String openAllayVersionRange;
    private final String openAllayApiVersionRange;

    public SupportTarget(
            String loader,
            String minecraftVersionRange,
            String openAllayVersionRange,
            String openAllayApiVersionRange) {
        this.loader = ApiValidation.loader(loader);
        this.minecraftVersionRange = ApiValidation.range(minecraftVersionRange, "minecraftVersionRange");
        this.openAllayVersionRange = ApiValidation.range(openAllayVersionRange, "openAllayVersionRange");
        this.openAllayApiVersionRange = ApiValidation.range(openAllayApiVersionRange, "openAllayApiVersionRange");
    }

    public String loader() { return loader; }
    public String minecraftVersionRange() { return minecraftVersionRange; }
    public String openAllayVersionRange() { return openAllayVersionRange; }
    public String openAllayApiVersionRange() { return openAllayApiVersionRange; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SupportTarget)) return false;
        SupportTarget that = (SupportTarget) other;
        return Objects.equals(loader, that.loader) &&
                Objects.equals(minecraftVersionRange, that.minecraftVersionRange) &&
                Objects.equals(openAllayVersionRange, that.openAllayVersionRange) &&
                Objects.equals(openAllayApiVersionRange, that.openAllayApiVersionRange);
    }
    @Override public int hashCode() { return Objects.hash(loader, minecraftVersionRange, openAllayVersionRange, openAllayApiVersionRange); }
}
