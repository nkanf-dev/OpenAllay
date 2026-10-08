package dev.openallay.guide;

import dev.openallay.guide.export.GuideSessionExportSnapshot;
import dev.openallay.guide.history.*;
import dev.openallay.guide.semantic.*;
import dev.openallay.guide.ui.*;
import dev.openallay.util.Java8Collections;
import java.time.*;
import java.util.*;

/** Known actual values remain identical across original sealed and runtime-admitted sources. */
public final class GuideUnionKnownFixture {
    public static void main(String[] args){UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");Instant now=Instant.parse("2026-01-01T00:00:00Z");
        SemanticInline.Text text=new SemanticInline.Text("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","known");SemanticBlock.Paragraph block=new SemanticBlock.Paragraph("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticInline>singletonList(text));SemanticDocument document=SemanticDocument.of(Collections.<SemanticBlock>singletonList(block),Collections.<SemanticDiagnostic>emptyList());
        GuideTimelineEntry.User timeline=new GuideTimelineEntry.User(0,id,"question");GuideHistoryScope scope=GuideHistoryScope.derive(id,GuideHistoryScope.Kind.SINGLEPLAYER,"fixture-world");GuideHistoryCommit commit=new GuideHistoryCommit(scope,Collections.<GuideHistoryMutation>singletonList(new GuideHistoryMutation.UpsertTimelineEntry(id,timeline)));
        GuideSessionExportSnapshot.Request exported=new GuideSessionExportSnapshot.Request(id,now,GuideRequestStatus.PREPARING,"question",Collections.<GuideSessionExportSnapshot.Entry>singletonList(new GuideSessionExportSnapshot.Entry.User(id,"question")),Collections.<dev.openallay.model.ModelMessage>emptyList(),null);
        GuideToolSummaryPresenter.Summary summary=new GuideToolSummaryPresenter.Summary("id","title","key","description",GuideToolDisplayStatus.SUCCEEDED,Collections.<GuideToolSummaryPresenter.Capsule>emptyList());
        RichComponent.StatusBadge rich=new RichComponent.StatusBadge("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",RichComponent.BadgeState.INFO,"known","known","known");
        GuideDetailCard.Error card=new GuideDetailCard.Error("known");GuideUiRow.Status row=new GuideUiRow.Status(id,GuideRequestStatus.PREPARING,"known",null);
        GuideHistoryDeleteScope deletion=GuideHistoryDeleteScope.actor(id);
        System.out.println("rich="+rich);System.out.println("card="+card);System.out.println("row="+row);System.out.println("delete="+deletion);
        System.out.println("document="+document);System.out.println("commit="+commit);System.out.println("export="+exported);System.out.println("summary="+summary);System.out.println("PASS knownGuideunion original/candidate semantic oracle");
    }
}
