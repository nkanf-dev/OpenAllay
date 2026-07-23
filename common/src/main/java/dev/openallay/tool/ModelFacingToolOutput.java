package dev.openallay.tool;

/**
 * Supplies a compact natural-text projection for the model while the normalized structured output
 * remains authoritative for validation, traces, UI cards, and evidence inspection.
 */
public interface ModelFacingToolOutput {
    String modelText();
}
