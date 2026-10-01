package dev.openallay.tool;

/** A declared result view whose complete value lives in the active request workspace. */
public interface WorkspaceModelFacingToolOutput extends ModelFacingToolOutput {
    ModelResultView modelView();
}
