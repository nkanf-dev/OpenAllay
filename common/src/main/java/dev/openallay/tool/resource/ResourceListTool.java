package dev.openallay.tool.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.resource.vfs.ResourceDirectoryPage;
import dev.openallay.resource.vfs.ResourceFileSystem;
import dev.openallay.resource.vfs.ResourcePath;
import dev.openallay.resource.vfs.ResourcePresentation;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;

public final class ResourceListTool extends ResourceToolSupport<ResourceListTool.Input> {
    @ToolDescription("Batch direct-child listings; never recursively dumps a resource tree")
    public record Input(
            @ToolDescription("Absolute virtual paths to list in one call; use / to discover available mounts")
                    List<String> paths,
            @ToolDescription("Include child kind and label metadata") @ToolOptional Boolean includeMetadata,
            @ToolDescription("Semantic continuation cursor from a previous result") @ToolOptional String cursor) {
        public Input(List<String> paths, Boolean includeMetadata) {
            this(paths, includeMetadata, null);
        }
    }

    private static final ToolDescriptor<Input, ResourceToolOutput> DESCRIPTOR = new ToolDescriptor<>(
            "openallay:resource_list",
            "List direct children of one or more OpenAllay virtual resource paths. Batch independent paths in one call.",
            Input.class, ResourceToolOutput.class, ToolAccess.READ_ONLY, REQUIRED_CONTEXT);
    private final ResourceFileSystem fileSystem;

    public ResourceListTool(RequestResourceContext resources) {
        this(resources, new ResourceFileSystem());
    }

    ResourceListTool(RequestResourceContext resources, ResourceFileSystem fileSystem) {
        super(resources);
        this.fileSystem = fileSystem;
    }

    @Override public ToolDescriptor<Input, ResourceToolOutput> descriptor() { return DESCRIPTOR; }

    @Override
    protected ToolResult<ResourceToolOutput> execute(
            RequestResourceContext.Session session, ToolInvocationContext context, Input input) {
        if (input != null && input.cursor() != null && !input.cursor().isBlank()) {
            if (input.paths() != null && !input.paths().isEmpty()) {
                return new ToolResult.Failure<>("invalid_arguments", "cursor continuation cannot include new paths");
            }
            return continueCursor(session, context, input.cursor(), GSON.toJson(input));
        }
        if (input == null || input.paths() == null || input.paths().isEmpty()) {
            return new ToolResult.Failure<>("invalid_arguments", "paths must contain at least one virtual path");
        }
        boolean metadata = !Boolean.FALSE.equals(input.includeMetadata());
        ArrayList<ResourceToolOutput.Item> items = new ArrayList<>();
        ArrayList<ResourcePath> parsedPaths = new ArrayList<>();
        for (int inputIndex = 0; inputIndex < input.paths().size(); inputIndex++) {
            String source = input.paths().get(inputIndex);
            if ("/".equals(source)) {
                items.add(success(inputIndex, source, root(session, metadata)));
                continue;
            }
            ResourcePath path;
            try {
                path = ResourcePath.parse(source);
            } catch (RuntimeException invalidPath) {
                items.add(failure(inputIndex, source,
                        new dev.openallay.resource.vfs.ResourceOperationFailure(
                                "invalid_resource_path", null, null, invalidPath.getMessage())));
                continue;
            }
            parsedPaths.add(path);
            ResourceFileSystem.OperationResult<ResourceDirectoryPage> result =
                    fileSystem.list(session.view(), List.of(path)).getFirst();
            if (!result.succeeded()) {
                items.add(failure(inputIndex, source, result.failure()));
                continue;
            }
            ResourceDirectoryPage page = result.value();
            JsonObject value = new JsonObject();
            value.addProperty("path", page.path().toString());
            value.addProperty("generation", page.generationId());
            JsonArray children = new JsonArray();
            page.entries().forEach(entry -> {
                if (metadata) {
                    JsonObject child = new JsonObject();
                    child.addProperty("path", entry.path().toString());
                    child.addProperty("kind", entry.kind().name().toLowerCase());
                    child.addProperty("label", entry.label());
                    children.add(child);
                } else {
                    children.add(entry.path().toString());
                }
            });
            value.add("children", children);
            items.add(success(inputIndex, source, value));
        }
        List<ResourcePath> existing = existingInputs(session, parsedPaths);
        return publish(session, context, "resource_list", GSON.toJson(input), items, existing, existing,
                ResourcePresentation.Kind.TABLE);
    }

    private static JsonObject root(RequestResourceContext.Session session, boolean metadata) {
        JsonObject value = new JsonObject();
        value.addProperty("path", "/");
        JsonArray children = new JsonArray();
        session.view().generationIds().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String path = "/" + entry.getKey();
                    if (!metadata) {
                        children.add(path);
                        return;
                    }
                    JsonObject child = new JsonObject();
                    child.addProperty("path", path);
                    child.addProperty("kind", "directory");
                    child.addProperty("label", entry.getKey());
                    child.addProperty("generation", entry.getValue());
                    children.add(child);
                });
        value.add("children", children);
        return value;
    }
}
