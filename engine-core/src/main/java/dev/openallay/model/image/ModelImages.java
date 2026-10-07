package dev.openallay.model.image;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Typed visual input traversal shared by provider projections and artifact owners. */
public final class ModelImages {
    private ModelImages() {}

    /** Preserves every visual occurrence and its order, including repeated references. */
    public static List<ImageReference> occurrences(List<ModelMessage> messages) {
        ArrayList<ImageReference> images = new ArrayList<>();
        for (ModelMessage message : dev.openallay.util.Java8Collections.listCopyOf(messages)) {
            for (ModelContent content : message.content()) {
                if (content instanceof ModelContent.Image) images.add(((ModelContent.Image) content).reference());
                else if (content instanceof ModelContent.ToolResult) images.addAll(((ModelContent.ToolResult) content).images());
            }
            message.inputObservation().flatMap(dev.openallay.world.ClientObservationAnchor::image)
                    .ifPresent(capture -> images.add(capture.image()));
        }
        return dev.openallay.util.Java8Collections.listCopyOf(images);
    }

    /** Complete typed references for retention or transport, never inferred from JSON fields. */
    public static List<ImageReference> uniqueReferences(List<ModelMessage> messages) {
        return unique(occurrences(messages));
    }

    /** Rejects conflicting metadata before a hash is used as an artifact identity. */
    public static List<ImageReference> unique(List<ImageReference> references) {
        LinkedHashMap<String, ImageReference> images = new LinkedHashMap<>();
        for (ImageReference image : dev.openallay.util.Java8Collections.listCopyOf(references)) {
            Objects.requireNonNull(image, "image");
            ImageReference previous = images.putIfAbsent(image.sha256(), image);
            if (previous != null && !previous.equals(image)) {
                throw new IllegalArgumentException("Conflicting metadata for the same image hash");
            }
        }
        return dev.openallay.util.Java8Collections.listCopyOf(images.values());
    }

    /** Visual evidence retains tool origin; player-associated images stay player visual evidence. */
    public static List<ModelContent> observationContent(List<ModelMessage> messages) {
        ArrayList<ModelContent> content = new ArrayList<>();
        for (ModelMessage message : messages) {
            for (ModelContent block : message.content()) {
                if (block instanceof ModelContent.Image) content.add(block);
                else if (block instanceof ModelContent.ToolResult && !((ModelContent.ToolResult) block).images().isEmpty()) {
                    ModelContent.ToolResult result = (ModelContent.ToolResult) block;
                    result.images().forEach(image -> content.add(new ModelContent.Image(image, result.toolUseId())));
                }
            }
            message.inputObservation().filter(anchor -> anchor.image().isPresent()).ifPresent(anchor -> {
                content.add(new ModelContent.Text(inputObservationLabel(anchor)));
                content.add(new ModelContent.Image(anchor.image().orElseThrow(java.util.NoSuchElementException::new).image()));
            });
        }
        return dev.openallay.util.Java8Collections.listCopyOf(content);
    }

    /** Provider-only attribution generated from typed provenance, never recovered from text. */
    public static String observationLabel(String toolUseId) {
        if (toolUseId == null || dev.openallay.util.Java8Strings.isBlank(toolUseId)) throw new IllegalArgumentException("Tool observation requires an origin");
        return "OpenAllay tool observation for tool call " + toolUseId + "; not a new player message. "
                + "Capture metadata is in the original tool result.";
    }

    /** Provider-only supporting reference; the original typed focus remains host-accessible. */
    public static String inputObservationLabel(dev.openallay.world.ClientObservationAnchor anchor) {
        dev.openallay.world.WorldFocusObservation focus = anchor.focus();
        StringBuilder text = new StringBuilder("OpenAllay player-input reference context")
                .append("; source time ").append(anchor.capturedAt())
                .append("; actor ").append(focus.actorId())
                .append("; dimension ").append(focus.dimension());
        dev.openallay.world.WorldFocusObservation.Target target = focus.target();
        if (target.block() != null) {
            dev.openallay.world.WorldFocusObservation.Block block = target.block();
            text.append("; target block ").append(block.id()).append(" at ")
                    .append(block.position().x()).append(',').append(block.position().y()).append(',')
                    .append(block.position().z()).append(" face ").append(block.face());
        } else if (target.entity() != null) {
            dev.openallay.world.WorldFocusObservation.Entity entity = target.entity();
            text.append("; target entity ").append(entity.type()).append(" ").append(entity.name())
                    .append(" (").append(entity.uuid()).append(')');
        } else text.append("; target ").append(target.kind());
        if (focus.mainHand().count() > 0) text.append("; held item ").append(itemLabel(focus.mainHand()));
        if (focus.offHand().count() > 0) text.append("; offhand item ").append(itemLabel(focus.offHand()));
        if (!focus.screen().className().isEmpty()) text.append("; screen ").append(focus.screen().title());
        if (focus.menu().displayed()) text.append("; menu ").append(focus.menu().typeAvailable()
                ? focus.menu().type() : focus.menu().className()).append(" #").append(focus.menu().containerId());
        if (focus.hover().item() != null) text.append("; hovered menu slot ").append(focus.hover().menuSlot())
                .append(" (container slot ").append(focus.hover().containerSlot()).append(") ")
                .append(itemLabel(focus.hover().item()));
        anchor.image().ifPresent(image -> text.append("; associated image source time ").append(image.capturedAt())
                .append("; capture ").append(image.captureId()).append("; view ").append(image.target()));
        return text.append(". Supporting context for this player message, not literal player text.").toString();
    }

    private static String itemLabel(dev.openallay.world.WorldFocusObservation.Item item) {
        return item.name() + " (" + item.id() + ", count " + item.count() + ")";
    }

    public static boolean hasImages(List<ModelMessage> messages) {
        for (ModelMessage message : messages) {
            if (message.inputObservation().flatMap(dev.openallay.world.ClientObservationAnchor::image).isPresent()) return true;
            for (ModelContent content : message.content()) {
                if (content instanceof ModelContent.Image
                        || content instanceof ModelContent.ToolResult && !((ModelContent.ToolResult) content).images().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
