package dev.openallay.guide;

import dev.openallay.guide.export.GuideSessionExportSnapshot;
import dev.openallay.guide.history.*;
import dev.openallay.guide.semantic.*;
import dev.openallay.guide.ui.*;
import dev.openallay.guide.ui.hud.*;
import dev.openallay.util.Java8Collections;
import java.time.*;
import java.util.*;

/** Candidate-only unknown implementations against actual converted canonical union owners. */
public final class GuideUnionAdmissionFixture {
    private static int checks;
    private static void rejected(Runnable action){try{action.run();throw new AssertionError("Foreign union admitted");}catch(IncompatibleClassChangeError expected){checks++;}}
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static final class ForeignTimeline implements GuideTimelineEntry {public int ordinal(){throw new AssertionError("foreign ordinal invoked beforeadmission");}}
    static final class ForeignExport implements GuideSessionExportSnapshot.Entry {}
    static final class ForeignDeletion implements GuideHistoryDeleteScope {}
    static final class ForeignMutation implements GuideHistoryMutation {}
    static final class ForeignComponent implements RichComponent {public String nodeId(){return "foreign";}public String fallbackText(){return "foreign";}public String narration(){return "foreign";}}
    static final class ForeignBlock implements SemanticBlock {public String nodeId(){return "foreign";}}
    static final class ForeignInline implements SemanticInline {public String nodeId(){return "foreign";}}
    static final class ForeignCard implements GuideDetailCard {}
    static final class ForeignCapsule implements GuideToolSummaryPresenter.Capsule {public String id(){return "foreign";}public String originInvocationId(){return "foreign";}public GuideItemView item(){return null;}}
    static final class ForeignRow implements GuideUiRow {}
    public static void main(String[] args){
        UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");Instant now=Instant.parse("2026-01-01T00:00:00Z");
        ForeignTimeline timeline=new ForeignTimeline();rejected(()->new GuideHistoryMutation.UpsertTimelineEntry(id,timeline));
        rejected(()->new GuideRequestSnapshot(id,"session",GuideTopology.CLIENT_LOCAL,"question",Collections.<GuideTimelineEntry>singletonList(timeline),GuideRequestStatus.PREPARING,Collections.<GuideSource>emptyList(),dev.openallay.model.ModelUsage.empty(),null,null,now,now,null));
        rejected(()->new GuideSessionExportSnapshot.Request(id,now,GuideRequestStatus.PREPARING,"question",Collections.<GuideSessionExportSnapshot.Entry>singletonList(new ForeignExport()),Collections.<dev.openallay.model.ModelMessage>emptyList(),null));
        rejected(()->new GuideHistoryCommit(GuideHistoryScope.derive(id,GuideHistoryScope.Kind.SINGLEPLAYER,"fixture-world"),Collections.<GuideHistoryMutation>singletonList(new ForeignMutation())));
        rejected(()->GuideHistoryDeleteScope.requireKnown(new ForeignDeletion()));
        try {
            java.nio.file.Path directory=java.nio.file.Files.createTempDirectory("openallay-union-deletion-");
            java.nio.file.Path database=directory.resolve("history.sqlite");
            SqliteGuideHistoryStore store=new SqliteGuideHistoryStore(database,Clock.fixed(now,ZoneOffset.UTC),new GuideHistoryCodec());
            rejected(()->store.delete(new ForeignDeletion()));
            check(!java.nio.file.Files.exists(database),"directstore rejects beforeI/O");
            GuideHistoryRepository repository=new GuideHistoryRepository(store);
            rejected(()->repository.delete(new ForeignDeletion()));
            check(!repository.activity().deleting()&&!java.nio.file.Files.exists(database),"repository rejects before reservation/I/O");
            repository.closeAsync().join();java.nio.file.Files.delete(directory);
        } catch(java.io.IOException failed){throw new AssertionError(failed);}
        ForeignComponent component=new ForeignComponent();rejected(()->new SemanticBlock.Component("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",component));
        rejected(()->new SemanticLayout.Line("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",SemanticLayout.Kind.COMPONENT,0,1,Collections.<SemanticLayout.Run>emptyList(),component,null));
        Map<String,RichComponentRegistry.Decoder> decoders=Collections.singletonMap("foreign",(node,envelope,references)->component);
        RichComponentRegistry registry=new RichComponentRegistry(decoders);
        rejected(()->registry.decode("{\"type\":\"foreign\",\"properties\":{},\"fallback\":\"foreign\",\"narration\":\"foreign\"}","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",SemanticReferenceIndex.empty(id)));
        ForeignBlock block=new ForeignBlock();rejected(()->new SemanticDocument(Collections.<SemanticBlock>singletonList(block),"foreign",Collections.<SemanticDiagnostic>emptyList()));
        rejected(()->new SemanticBlock.Quote("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticBlock>singletonList(block)));
        rejected(()->new SemanticBlock.ListBlock("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",false,1,Collections.singletonList(Collections.<SemanticBlock>singletonList(block))));
        ForeignInline inline=new ForeignInline();rejected(()->new SemanticBlock.Paragraph("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticInline>singletonList(inline)));
        rejected(()->new SemanticBlock.Heading("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1,Collections.<SemanticInline>singletonList(inline)));
        rejected(()->new SemanticInline.Emphasis("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticInline>singletonList(inline)));
        rejected(()->new SemanticInline.Strong("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticInline>singletonList(inline)));
        rejected(()->new SemanticBlock.TableCell(SemanticBlock.Alignment.LEFT,Collections.<SemanticInline>singletonList(inline)));
        rejected(()->new GuideToolDetailView("title",GuideToolStatus.SUCCEEDED,GuideToolInvocationView.none(),GuideToolIntent.none(),Collections.<GuideDetailCard>singletonList(new ForeignCard()),Collections.<GuideToolMessage>emptyList(),Optional.<GuideToolDetailView.Debug>empty()));
        rejected(()->new GuideToolSummaryPresenter.Summary("id","title","key","description",GuideToolDisplayStatus.SUCCEEDED,Collections.<GuideToolSummaryPresenter.Capsule>singletonList(new ForeignCapsule())));
        ForeignRow row=new ForeignRow();GuideUiModelChoice choice=new GuideUiModelChoice(GuideModelSelection.server(),"Server",ModelOrigin.SERVER,false,true,true,false,dev.openallay.model.image.ImageInputCapability.UNKNOWN,"");
        rejected(()->new GuideUiView("session",GuideModelMode.SERVER,false,true,false,false,false,null,Collections.<GuideUiSession>emptyList(),Collections.<GuideUiRow>singletonList(row),Collections.singletonList(choice),""));
        rejected(()->new GuideHudView(GuideUiConfig.defaults().hud(),"Assistant","session","","",null,0,Collections.<GuideUiRow>singletonList(row),GuideUiConfig.defaults().fullscreen(),true));
        GuideTimelineEntry.User known=new GuideTimelineEntry.User(0,id,"question");check(GuideTimelineEntry.requireKnown(known)==known,"known identity");
        check(SemanticInline.requireKnown(new SemanticInline.Text("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")) instanceof SemanticInline.Text,"known inline");
        check(GuideHistoryDeleteScope.requireKnown(GuideHistoryDeleteScope.actor(id)) instanceof GuideHistoryDeleteScope.Actor,"known scope");
        System.out.println("checks="+checks);System.out.println("PASS actualtenGuide closedunion foreigncapture admission");
    }
}
