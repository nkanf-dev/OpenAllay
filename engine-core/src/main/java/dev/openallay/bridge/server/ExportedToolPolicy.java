package dev.openallay.bridge.server;

import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.util.Optional;
import java.util.Set;

public final class ExportedToolPolicy {
    private final ToolRegistry tools;
    private final Set<String> exported;

    public ExportedToolPolicy(ToolRegistry tools, Set<String> exported) {
        this.tools = tools;
        this.exported = Set.copyOf(exported);
        for (String id : this.exported) {
            Tool<?, ?> tool = tools.find(id).orElseThrow(() ->
                    new IllegalArgumentException("Cannot export unknown tool " + id));
            if (!isRemotelyReadable(tool.descriptor())) {
                throw new IllegalArgumentException("Cannot remotely export non-read-only tool " + id);
            }
        }
    }

    public Optional<Tool<?, ?>> find(String id) {
        return exported.contains(id) ? tools.find(id) : Optional.empty();
    }

    public Set<String> ids() {
        return exported;
    }

    public void closeRequestScope(String correlationId) {
        exported.stream()
                .map(tools::find)
                .flatMap(Optional::stream)
                .filter(dev.openallay.tool.RequestScopeParticipant.class::isInstance)
                .map(dev.openallay.tool.RequestScopeParticipant.class::cast)
                .forEach(participant -> participant.closeRequestScope(correlationId));
    }

    /**
     * The server projection of run_javascript is read-only because server requests never capture
     * unrestricted JavaScript authority or a command bridge. Program text is not a permission.
     */
    public static boolean isRemotelyReadable(ToolDescriptor<?, ?> descriptor) {
        return descriptor.access() == ToolAccess.READ_ONLY
                || descriptor.id().equals(RunJavascriptTool.ID);
    }
}
