package dev.openallay.guide.ui;

import dev.openallay.guide.GuideToolMessage;
import java.util.List;

/** Player Step Flow facts. Native bodies come only from the existing typed detail projection. */
public final class GuideToolStepFlowPresenter {
    private GuideToolStepFlowPresenter() {}

    public static boolean partial(GuideUiRow.Tool tool) {
        return tool.detail().cards().stream().anyMatch(card -> switch (card) {
            case GuideDetailCard.Table table -> !table.complete();
            case GuideDetailCard.KeyValue values -> !values.complete();
            case GuideDetailCard.DataPreview preview -> !preview.complete();
            default -> false;
        }) || tool.detail().narration().stream().anyMatch(message -> switch (message.key()) {
            case ANALYSIS_PREVIEW, ANALYSIS_FIELDS_PREVIEW, ANALYSIS_VALUE_PREVIEW -> true;
            default -> false;
        });
    }

    public static List<GuideToolMessage> messages(GuideUiRow.Tool tool) {
        GuideToolDetailView detail = tool.detail();
        if (detail.failure().isPresent()) return List.of();
        if (detail.displayStatus() == GuideToolDisplayStatus.NO_RESULT_RECORDED) {
            return List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_DETAIL_NOT_STORED));
        }
        // A completed result is not an engineering field-count header. Preview scope stays visible.
        return detail.narration().stream()
                .map(message -> message.key() == GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW
                        ? GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_VALUE_PREVIEW) : message)
                .filter(message -> switch (message.key()) {
                    case INVOCATION_RUN_JAVASCRIPT, ANALYSIS_COMPLETE, ANALYSIS_FIELDS_COMPLETE,
                            ANALYSIS_VALUE_COMPLETE, ANALYSIS_WORKSPACE -> false;
                    case RESULT_COMPLETED -> detail.cards().isEmpty();
                    default -> true;
                }).toList();
    }
}
