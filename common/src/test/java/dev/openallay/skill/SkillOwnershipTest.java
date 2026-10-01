package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ownership is rebuilt from actual retained instruction slices, not successful load history. */
final class SkillOwnershipTest {
    private static final String BODY = "A".repeat(8_192) + "B".repeat(8_192) + "C".repeat(6_000);

    @Test
    void fullPlaintextCoverageOwnsEveryChunkAndReportsFullWithoutCopyingTheBody() {
        Fixture fixture = fixture();
        RetainedSkillContext retained = new RetainedSkillContext();
        List<ModelMessage> history = flatten(fixture.chunks());

        List<ModelMessage> projection = fixture.instructions().refresh(history, retained);
        fixture.instructions().reconcile(projection, retained);

        assertEquals(history, projection);
        assertEquals(3, retained.ranges().size());
        for (Loaded chunk : fixture.chunks()) {
            assertReceipt(chunk.output(), fixture.instructions().reuse(chunk.input(), retained));
        }
        String manifest = fixture.instructions().manifest(retained);
        assertTrue(manifest.contains("guide / SKILL.md: full"));
        assertTrue(manifest.contains("available_refs=[references/a.md, references/b.md]"));
        assertFalse(manifest.contains("missing_offset="));
        assertFalse(manifest.contains(BODY));
        assertEquals(BODY, fixture.chunks().stream().map(chunk -> chunk.output().content())
                .collect(java.util.stream.Collectors.joining()));
    }

    @Test
    void tailOnlyCoverageDoesNotOwnTheMissingPrefixOrInventAFullDocument() {
        Fixture fixture = fixture();
        RetainedSkillContext retained = new RetainedSkillContext();
        Loaded first = fixture.chunks().getFirst();
        Loaded middle = fixture.chunks().get(1);
        Loaded tail = fixture.chunks().getLast();
        fixture.tool().prepareContext("tail", tail.history(), retained);

        assertEquals(1, retained.ranges().size());
        assertNull(fixture.instructions().reuse(first.input(), retained));
        assertNull(fixture.instructions().reuse(middle.input(), retained));
        assertReceipt(tail.output(), fixture.instructions().reuse(tail.input(), retained));
        String manifest = fixture.tool().manifest("tail");
        assertTrue(manifest.contains("guide / SKILL.md: partial ["
                + tail.output().offset() + ".." + tail.output().nextOffset() + "]"));
        assertTrue(manifest.contains("missing_offset=0"));
        assertFalse(manifest.contains(": full"));
        assertFalse(manifest.contains("; cursor="));

        LoadSkillTool.Output prefixAgain = success(fixture.tool().invoke(request("tail"), first.input()));
        assertEquals(LoadSkillTool.LoadState.CONTENT, prefixAgain.state());
        assertEquals(first.output().content(), prefixAgain.content());
        assertEquals(1, retained.ranges().size());
    }

    @Test
    void aMiddleHoleReturnsItsExactCursorAndDoesNotGrantTheAbsentRange() {
        Fixture fixture = fixture();
        Loaded first = fixture.chunks().getFirst();
        Loaded middle = fixture.chunks().get(1);
        Loaded tail = fixture.chunks().getLast();
        List<ModelMessage> history = flatten(List.of(first, tail));
        RetainedSkillContext retained = new RetainedSkillContext();
        fixture.tool().prepareContext("hole", history, retained);

        assertReceipt(first.output(), fixture.instructions().reuse(first.input(), retained));
        assertNull(fixture.instructions().reuse(middle.input(), retained));
        assertReceipt(tail.output(), fixture.instructions().reuse(tail.input(), retained));
        String manifest = fixture.tool().manifest("hole");
        assertTrue(manifest.contains("partial [0.." + first.output().nextOffset()
                + ", " + tail.output().offset() + ".." + tail.output().nextOffset() + "]"));
        assertTrue(manifest.contains("missing_offset=" + middle.output().offset()));
        assertTrue(manifest.contains("cursor=" + middle.input().cursor()));
        assertFalse(manifest.contains(": full"));

        LoadSkillTool.Output missing = success(fixture.tool().invoke(request("hole"), middle.input()));
        assertEquals(LoadSkillTool.LoadState.CONTENT, missing.state());
        assertEquals(middle.output().offset(), missing.offset());
        assertEquals(middle.output().content(), missing.content());
        assertEquals(2, retained.ranges().size());

        List<ModelMessage> filled = new ArrayList<>(history);
        filled.addAll(history("filled-hole", middle.input(), missing));
        fixture.tool().prepareContext("hole", filled, retained);
        assertTrue(fixture.tool().manifest("hole").contains("guide / SKILL.md: full"));
    }

    @Test
    void duplicatePlaintextBecomesAReceiptOnlyInTheProjectionAndKeepsItsSuccessFlag() {
        Fixture fixture = fixture();
        Loaded chunk = fixture.chunks().getFirst();
        List<ModelMessage> history = new ArrayList<>(chunk.history());
        history.addAll(history("duplicate", chunk.input(), chunk.output()));
        List<ModelMessage> original = List.copyOf(history);
        RetainedSkillContext retained = new RetainedSkillContext();

        List<ModelMessage> projection = fixture.instructions().refresh(history, retained);
        fixture.instructions().reconcile(projection, retained);

        assertEquals(original, history);
        assertEquals(chunk.output().modelText(), resultText(history.get(3)));
        assertEquals(history.get(2), projection.get(2));
        assertTrue(resultText(projection.get(3)).contains("state: already_loaded"));
        assertFalse(result(projection.get(3)).error());
        assertFalse(resultText(projection.get(3)).contains(chunk.output().content()));
        assertEquals(1, retained.ranges().size());
        assertEquals(projection, fixture.instructions().refresh(projection, retained));
    }

    @Test
    void changedChunkTextHeaderRangeAndContinuationAreNeverOwned() {
        Fixture fixture = fixture();
        Loaded chunk = fixture.chunks().get(1);
        String valid = chunk.output().modelText();
        List<String> tampered = List.of(
                valid.replace("B".repeat(32), "D".repeat(32)),
                valid.replace("source: " + chunk.output().source(), "source: another_owner"),
                valid.replace("fingerprint: " + chunk.output().fingerprint(),
                        "fingerprint: " + "0".repeat(64)),
                valid.replace("range: " + chunk.output().offset() + ".." + chunk.output().nextOffset(),
                        "range: " + (chunk.output().offset() + 1) + ".." + chunk.output().nextOffset()),
                valid.replace("content_length: " + chunk.output().content().length(),
                        "content_length: " + (chunk.output().content().length() - 1)),
                valid.replace("state: content", "state: complete"),
                valid.replace("complete: false", "complete: true"),
                valid.replace(chunk.output().nextCursor(), "not-a-cursor"),
                valid.replace("references: references/a.md, references/b.md",
                        "references: assets/a.md"),
                valid.replace("references: references/a.md, references/b.md",
                        "references: references/../a.md"),
                valid.replace("references: references/a.md, references/b.md",
                        "references: references/a.md\t"),
                valid.replace("references: references/a.md, references/b.md",
                        "references: references/a.md" + (char) 127),
                valid + "\nsummary of omitted content");

        for (String text : tampered) {
            List<ModelMessage> altered = List.of(chunk.history().getFirst(),
                    new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                            "chunk-1", new JsonPrimitive(text), false))));
            RetainedSkillContext retained = new RetainedSkillContext();
            List<ModelMessage> projection = fixture.instructions().refresh(altered, retained);
            fixture.instructions().reconcile(projection, retained);

            assertInvalidated(projection.get(1));
            assertEquals(text, resultText(altered.get(1)));
            assertEquals(List.of(), retained.ranges());
            assertNull(fixture.instructions().reuse(chunk.input(), retained));
            assertEquals("", fixture.instructions().manifest(retained));
        }

        // Historical refs metadata does not own bytes or become the current refs manifest.
        List<String> historicalMetadata = List.of(
                "references: references/b.md, references/a.md",
                "references: references/a.md, references/missing.md");
        for (String metadata : historicalMetadata) {
            String text = valid.replace("references: references/a.md, references/b.md", metadata);
            List<ModelMessage> history = List.of(chunk.history().getFirst(),
                    new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                            "chunk-1", new JsonPrimitive(text), false))));
            RetainedSkillContext retained = new RetainedSkillContext();
            List<ModelMessage> projection = fixture.instructions().refresh(history, retained);
            fixture.instructions().reconcile(projection, retained);

            assertEquals(history, projection);
            assertReceipt(chunk.output(), fixture.instructions().reuse(chunk.input(), retained));
            assertTrue(fixture.instructions().manifest(retained)
                    .contains("available_refs=[references/a.md, references/b.md]"));
            assertFalse(fixture.instructions().manifest(retained).contains("references/missing.md"));
        }
    }

    @Test
    void remoteValidationChecksTheFrozenChunkIdentityAndNeverTreatsValidationAsOwnership() {
        Fixture fixture = fixture();
        Loaded chunk = fixture.chunks().get(1);
        RetainedSkillContext retained = new RetainedSkillContext();
        LoadSkillTool.Output output = chunk.output();

        assertTrue(fixture.instructions().validate(chunk.input(), output));
        assertFalse(fixture.instructions().validate(fixture.chunks().getFirst().input(), output));
        assertFalse(fixture.instructions().validate(chunk.input(), copy(output, output.source(),
                "D".repeat(output.content().length()), output.nextCursor())));
        assertFalse(fixture.instructions().validate(chunk.input(), copy(output, "other_owner",
                output.content(), output.nextCursor())));
        assertFalse(fixture.instructions().validate(chunk.input(), copy(output, output.source(),
                output.content(), "not-a-cursor")));
        LoadSkillTool.Output missingReferenceMetadata = new LoadSkillTool.Output(output.name(), output.document(),
                output.source(), output.fingerprint(), output.state(), output.content(), output.offset(),
                output.nextOffset(), output.complete(), output.nextCursor(), List.of(),
                output.allowedTools(), output.provenance());
        assertFalse(fixture.instructions().validate(chunk.input(), missingReferenceMetadata));
        assertNull(fixture.instructions().reuse(chunk.input(), retained));
        assertEquals(List.of(), retained.ranges());

        SkillCatalogManifest manifest = fixture.tool().catalogManifest();
        assertFalse(manifest.toString().contains(output.content()));
        assertFalse(manifest.metadataPrompt().contains(BODY));
        assertEquals(fixture.catalog().find("guide").orElseThrow().instructions().length(),
                manifest.documents().stream().filter(document -> document.document().equals("SKILL.md"))
                        .findFirst().orElseThrow().length());
    }

    @Test
    void theSameBytesFromADifferentOwnerProvenanceOrOriginDoNotReuseOldOwnership() {
        String body = "Same instruction bytes.";
        SkillRepository repository = repository("guide", body, Map.of());
        SkillCatalogSnapshot originalCatalog = repository.snapshot(Set.of());
        LoadSkillTool originalTool = new LoadSkillTool(originalCatalog).withOwner("client");
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output old = success(originalTool.invokeFresh(request("old"), input));
        List<ModelMessage> history = history("old", input, old);
        RetainedSkillContext retained = new RetainedSkillContext();
        originalTool.prepareContext("old", history, retained);
        assertReceipt(old, new SkillInstructionContext(originalTool.catalogManifest()).reuse(input, retained));

        SkillRepository changedProvenance = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(changedProvenance.reload(List.of(source("different-pack", SkillSource.Origin.EXTERNAL,
                "guide", body, Map.of())), Set.of()));
        SkillRepository changedOrigin = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(changedOrigin.reload(List.of(source("pack", SkillSource.Origin.LOCAL,
                "guide", body, Map.of())), Set.of()));
        List<LoadSkillTool> changedTools = List.of(originalTool.withOwner("server"),
                new LoadSkillTool(changedProvenance.snapshot(Set.of())).withOwner("client"),
                new LoadSkillTool(changedOrigin.snapshot(Set.of())).withOwner("client"));

        for (LoadSkillTool changed : changedTools) {
            originalTool.prepareContext("old", history, retained);
            assertReceipt(old, new SkillInstructionContext(originalTool.catalogManifest()).reuse(input, retained));
            SkillInstructionContext instructions = new SkillInstructionContext(changed.catalogManifest());
            LoadSkillTool.Output fresh = success(changed.invokeFresh(request("changed"), input));
            assertEquals(old.content(), fresh.content());
            assertEquals(old.fingerprint(), fresh.fingerprint());
            assertNotEquals(old.source(), fresh.source());
            assertFalse(instructions.validate(input, old));
            assertNull(instructions.reuse(input, retained));
            List<ModelMessage> projection = instructions.refresh(history, retained);
            instructions.reconcile(projection, retained);
            assertInvalidated(projection.get(1));
            assertEquals(List.of(), retained.ranges());
            assertEquals(old.modelText(), resultText(history.get(1)));
        }
    }

    @Test
    void aReceiptSurvivingCompactionDoesNotOwnTheFormerInstructionRange() {
        Fixture fixture = fixture();
        Loaded first = fixture.chunks().getFirst();
        RetainedSkillContext retained = new RetainedSkillContext();
        fixture.tool().prepareContext("receipt", first.history(), retained);
        LoadSkillTool.Output receipt = success(fixture.tool().invoke(request("receipt"), first.input()));
        List<ModelMessage> receiptOnly = history("receipt-only", first.input(), receipt);

        List<ModelMessage> projection = fixture.instructions().refresh(receiptOnly, retained);
        fixture.tool().prepareContext("receipt", projection, retained);

        assertInvalidated(projection.get(1));
        assertEquals(receipt.modelText(), resultText(receiptOnly.get(1)));
        assertEquals(List.of(), retained.ranges());
        assertEquals("", fixture.tool().manifest("receipt"));
        assertNull(fixture.instructions().reuse(first.input(), retained));
        LoadSkillTool.Output reloaded = success(fixture.tool().invoke(request("receipt"), first.input()));
        assertEquals(LoadSkillTool.LoadState.CONTENT, reloaded.state());
        assertEquals(first.output().content(), reloaded.content());
    }

    @Test
    void summariesMetadataAndUnpairedInstructionTextDoNotOwnDocumentBytes() {
        Fixture fixture = fixture();
        Loaded first = fixture.chunks().getFirst();
        RetainedSkillContext retained = new RetainedSkillContext();
        fixture.tool().prepareContext("summary", first.history(), retained);
        List<ModelMessage> summaryOnly = List.of(
                ModelMessage.userText("guide was loaded; remember its evidence workflow."),
                ModelMessage.userText(first.output().modelText()),
                ModelMessage.userText(fixture.tool().catalogManifest().metadataPrompt()));

        List<ModelMessage> projection = fixture.instructions().refresh(summaryOnly, retained);
        fixture.tool().prepareContext("summary", projection, retained);

        assertEquals(summaryOnly, projection);
        assertEquals(List.of(), retained.ranges());
        assertEquals("", fixture.tool().manifest("summary"));
        assertEquals(LoadSkillTool.LoadState.CONTENT,
                success(fixture.tool().invoke(request("summary"), first.input())).state());

        List<ModelMessage> resultWithoutUse = List.of(first.history().get(1));
        fixture.tool().prepareContext("summary", resultWithoutUse, retained);
        assertEquals(List.of(), retained.ranges());
    }

    @Test
    void restoredPlaintextRebuildsANewSessionIndexWithoutReusingObjectIdentityOrOldFlags() {
        Fixture fixture = fixture();
        List<ModelMessage> history = flatten(fixture.chunks());
        RetainedSkillContext beforeRestore = new RetainedSkillContext();
        fixture.tool().prepareContext("before", history, beforeRestore);
        fixture.tool().closeRequestScope("before");
        List<ModelMessage> restoredHistory = history.stream().map(message -> new ModelMessage(message.role(),
                message.content().stream().map(item -> {
                    if (item instanceof ModelContent.ToolUse use) {
                        return (ModelContent) new ModelContent.ToolUse(use.id(), use.name(), use.input());
                    }
                    ModelContent.ToolResult result = (ModelContent.ToolResult) item;
                    return (ModelContent) new ModelContent.ToolResult(
                            result.toolUseId(), result.value(), result.error());
                }).toList())).toList();
        LoadSkillTool restoredTool = new LoadSkillTool(fixture.catalog()).withOwner("client");
        RetainedSkillContext restored = new RetainedSkillContext();
        SkillInstructionContext instructions = new SkillInstructionContext(restoredTool.catalogManifest());
        assertNull(instructions.reuse(fixture.chunks().getFirst().input(), restored));

        List<ModelMessage> projection = instructions.refresh(restoredHistory, restored);
        restoredTool.prepareContext("restored", projection, restored);

        assertEquals(history, restoredHistory);
        assertEquals(restoredHistory, projection);
        assertEquals(3, restored.ranges().size());
        assertTrue(restoredTool.manifest("restored").contains("guide / SKILL.md: full"));
        assertReceipt(fixture.chunks().getFirst().output(), success(restoredTool.invoke(
                request("restored"), fixture.chunks().getFirst().input())));
        instructions.reconcile(List.of(), beforeRestore);
        assertEquals(List.of(), beforeRestore.ranges());
        assertEquals(3, restored.ranges().size());
    }

    @Test
    void sameActorSessionsHaveIndependentRetainedIndexesAndPendingExchanges() {
        Fixture fixture = fixture();
        Loaded first = fixture.chunks().getFirst();
        ToolInvocationContext sessionA = request("session-a");
        ToolInvocationContext sessionB = request("session-b");
        assertEquals(sessionA.caller(), sessionB.caller());
        RetainedSkillContext retainedA = new RetainedSkillContext();
        RetainedSkillContext retainedB = new RetainedSkillContext();
        fixture.tool().prepareContext("session-a", first.history(), retainedA);
        fixture.tool().prepareContext("session-b", List.of(), retainedB);

        assertReceipt(first.output(), success(fixture.tool().invoke(sessionA, first.input())));
        LoadSkillTool.Output inB = success(fixture.tool().invoke(sessionB, first.input()));
        assertEquals(LoadSkillTool.LoadState.CONTENT, inB.state());
        assertEquals(first.output().content(), inB.content());
        assertEquals(1, retainedA.ranges().size());
        assertEquals(List.of(), retainedB.ranges());
        assertEquals("", fixture.tool().manifest("session-b"));

        fixture.tool().prepareContext("session-b", history("retained-b", first.input(), inB), retainedB);
        fixture.tool().prepareContext("session-a", List.of(), retainedA);
        assertEquals(List.of(), retainedA.ranges());
        assertEquals(1, retainedB.ranges().size());
        assertEquals(LoadSkillTool.LoadState.CONTENT,
                success(fixture.tool().invoke(sessionA, first.input())).state());
        assertReceipt(first.output(), success(fixture.tool().invoke(sessionB, first.input())));

        fixture.tool().closeRequestScope("session-b");
        assertEquals(1, retainedB.ranges().size());
        fixture.tool().prepareContext("rebound-b", history("retained-b", first.input(), inB), retainedB);
        assertReceipt(first.output(), success(fixture.tool().invoke(request("rebound-b"), first.input())));
    }

    @Test
    void onlyActuallyDeliveredSystemGuidanceOwnsTheBodyAndRedactionRevokesIt() {
        String name = SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT;
        String body = "Use evidence. Synthetic marker: synthetic-hidden-marker.";
        SkillRepository repository = repository(name, body, Map.of());
        SkillCatalogSnapshot catalog = repository.snapshot(Set.of()).forRequest(true);
        LoadSkillTool tool = new LoadSkillTool(catalog);
        SkillInstructionContext instructions = new SkillInstructionContext(tool.catalogManifest());
        RetainedSkillContext retained = new RetainedSkillContext();
        LoadSkillTool.Input input = new LoadSkillTool.Input(name);
        String delivered = "## CORE\nCore guidance.\n\n## UNRESTRICTED JAVASCRIPT GUIDANCE\n"
                + body + "\n\n## NEXT SECTION\nPlayer context.";
        assertNull(instructions.reuse(input, retained));

        instructions.prepareSystem(delivered, retained);
        tool.prepareContext("system", List.of(), retained);
        LoadSkillTool.Output receipt = instructions.reuse(input, retained);

        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, receipt.state());
        assertEquals("", receipt.content());
        assertEquals(1, retained.ranges().size());
        assertTrue(tool.manifest("system").contains(name + " / SKILL.md: full"));
        assertFalse(tool.manifest("system").contains(body));

        // This is the actual model-facing redacted system, not the unredacted source catalog.
        instructions.prepareSystem(delivered.replace("synthetic-hidden-marker", "[redacted]"), retained);
        List<ModelMessage> receiptOnly = history("system-receipt", input, receipt);
        List<ModelMessage> projection = instructions.refresh(receiptOnly, retained);
        tool.prepareContext("system", projection, retained);
        assertInvalidated(projection.get(1));
        assertEquals(receipt.modelText(), resultText(receiptOnly.get(1)));
        assertEquals(List.of(), retained.ranges());
        assertNull(instructions.reuse(input, retained));
        assertEquals("", tool.manifest("system"));

        instructions.prepareSystem("A summary says the system contained " + body, retained);
        instructions.reconcile(List.of(), retained);
        assertEquals(List.of(), retained.ranges());
    }

    @Test
    void originalCatalogIdentitiesMatchTheActualSystemAndReferenceDelivery() {
        String name = SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT;
        String playerFields = "token=quest-token password=castle-password";
        String body = "Use evidence. Player notes: " + playerFields + ".";
        String referenceBody = "Reference notes: " + playerFields + ".";
        SkillCatalogSnapshot catalog = repository(name, body,
                Map.of("references/a.md", referenceBody))
                .snapshot(Set.of()).forRequest(true);
        LoadSkillTool tool = new LoadSkillTool(catalog).withOwner("client");
        SkillInstructionContext instructions = new SkillInstructionContext(tool.catalogManifest());
        RetainedSkillContext retained = new RetainedSkillContext();
        LoadSkillTool.Input input = new LoadSkillTool.Input(name);
        instructions.prepareSystem("## UNRESTRICTED JAVASCRIPT GUIDANCE\n" + body, retained);
        tool.prepareContext("original-system", List.of(), retained);

        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, instructions.reuse(input, retained).state());
        LoadSkillTool.Output entry = success(tool.invokeFresh(unrestrictedRequest("original-system"), input));
        assertEquals(body, entry.content());
        assertTrue(entry.modelText().contains(playerFields));
        assertTrue(instructions.validate(input, entry));
        assertEquals(LoadSkillTool.fingerprint(body), entry.fingerprint());
        assertEquals(body, catalog.find(name).orElseThrow().instructions());
        assertEquals(tool.catalogManifest().documents().stream()
                .filter(document -> document.document().equals("SKILL.md")).findFirst().orElseThrow().source(),
                entry.source());

        LoadSkillTool.Input reference = new LoadSkillTool.Input(name, "references/a.md");
        LoadSkillTool.Output deliveredReference = success(tool.invokeFresh(
                unrestrictedRequest("original-system"), reference));
        assertEquals(referenceBody, deliveredReference.content());
        assertTrue(instructions.validate(reference, deliveredReference));
        assertTrue(deliveredReference.modelText().contains(playerFields));
        assertFalse(tool.catalogManifest().toString().contains(playerFields));
        assertTrue(tool.catalogManifest().documents().stream().anyMatch(document ->
                document.document().equals("SKILL.md")
                        && document.fingerprint().equals(LoadSkillTool.fingerprint(body))));
        assertEquals(LoadSkillTool.fingerprint(referenceBody), deliveredReference.fingerprint());
        assertEquals(entry.source(), deliveredReference.source());
    }

    @Test
    void originalDeliveryPreservesMetadataAndCallableIdentifiersAndUsesAnOpaqueSource() {
        String sentinel = "token-password";
        SkillSource richSource = new SkillSource("player-pack-" + sentinel, "guide/SKILL.md", Map.of(
                "guide/SKILL.md", """
                        ---
                        name: guide
                        description: Guide metadata token-password
                        license: License token-password
                        compatibility: Compatibility token-password
                        metadata:
                          token: quest-token
                          password: castle-password
                        ---
                        Instructions token-password.
                        """,
                "guide/references/a.md", "Reference token-password."));
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(repository.reload(List.of(richSource,
                source("pack", SkillSource.Origin.EXTERNAL, "path-guide", "Path guide.",
                        Map.of("references/" + sentinel + ".md", "Reference bytes.")),
                source("pack", SkillSource.Origin.EXTERNAL, sentinel, "Named guide.", Map.of()),
                source("pack", SkillSource.Origin.EXTERNAL, "other", "Unaffected guidance.", Map.of())), Set.of()));
        SkillCatalogSnapshot catalog = repository.snapshot(Set.of());
        LoadSkillTool tool = new LoadSkillTool(catalog).withOwner("client");
        SkillCatalogManifest manifest = tool.catalogManifest();
        SkillCatalogManifest.Document entry = manifest.documents().stream()
                .filter(document -> document.name().equals("guide") && document.document().equals("SKILL.md"))
                .findFirst().orElseThrow();
        SkillMetadata originalMetadata = catalog.find("guide").orElseThrow().metadata();
        String originalIdentity = "client\u0000" + originalMetadata.origin().name() + "\u0000"
                + originalMetadata.provenance();
        String sourceDigest = LoadSkillTool.fingerprint(originalMetadata.origin().name() + "\u0000"
                + originalMetadata.provenance());
        String ownerBoundIdentity = "client\u0000" + sourceDigest;
        String encodedOriginal = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                originalIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(LoadSkillTool.fingerprint(ownerBoundIdentity), entry.source());
        assertTrue(entry.source().matches("[a-f0-9]{64}"));
        assertNotEquals(encodedOriginal, entry.source());
        assertNotEquals(LoadSkillTool.fingerprint(originalIdentity), entry.source());
        assertEquals("Guide metadata " + sentinel, entry.description());
        assertTrue(manifest.toString().contains(sentinel));
        assertFalse(manifest.toString().contains(originalMetadata.provenance()));
        assertTrue(manifest.metadataPrompt().contains(sentinel));
        assertEquals(Set.of("guide", "other", "path-guide", sentinel), manifest.documents().stream()
                .map(SkillCatalogManifest.Document::name).collect(java.util.stream.Collectors.toSet()));
        assertFalse(java.util.Arrays.stream(SkillCatalogManifest.Document.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("provenance")));
        assertEquals("License " + sentinel, originalMetadata.license().orElseThrow());
        assertEquals("Compatibility " + sentinel, originalMetadata.compatibility().orElseThrow());
        assertEquals(Map.of("token", "quest-token", "password", "castle-password"), originalMetadata.attributes());

        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output delivered = success(tool.invokeFresh(request("original-metadata"), input));
        assertEquals("Instructions " + sentinel + ".", delivered.content());
        assertEquals(originalMetadata.provenance(), delivered.provenance());
        assertEquals(entry.source(), delivered.source());
        assertEquals(entry.fingerprint(), delivered.fingerprint());
        assertEquals(LoadSkillTool.fingerprint(delivered.content()), delivered.fingerprint());
        assertTrue(new SkillInstructionContext(manifest).validate(input, delivered));
        assertTrue(delivered.modelText().contains(sentinel));
        assertEquals("Guide metadata " + sentinel, originalMetadata.description());
        for (String preserved : List.of("path-guide", sentinel)) {
            LoadSkillTool.Input preservedInput = new LoadSkillTool.Input(preserved);
            LoadSkillTool.Output output = success(tool.invokeFresh(request("preserved"), preservedInput));
            assertEquals(preserved, output.name());
            assertTrue(new SkillInstructionContext(manifest).validate(preservedInput, output));
        }
        LoadSkillTool.Input reference = new LoadSkillTool.Input("path-guide", "references/" + sentinel + ".md");
        LoadSkillTool.Output referenceOutput = success(tool.invokeFresh(request("preserved-reference"), reference));
        assertEquals(reference.reference(), referenceOutput.document());
        assertEquals("Reference bytes.", referenceOutput.content());
        assertTrue(new SkillInstructionContext(manifest).validate(reference, referenceOutput));
    }

    @Test
    void systemOwnedPlaintextIsProjectedToAnIdempotentReceiptWithoutChangingTheTranscript() {
        String name = SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT;
        String body = "Use evidence in unrestricted mode.";
        SkillCatalogSnapshot catalog = repository(name, body, Map.of()).snapshot(Set.of()).forRequest(true);
        LoadSkillTool tool = new LoadSkillTool(catalog);
        ToolInvocationContext request = unrestrictedRequest("system");
        LoadSkillTool.Input input = new LoadSkillTool.Input(name);
        LoadSkillTool.Output content = success(tool.invokeFresh(request, input));
        SkillInstructionContext instructions = new SkillInstructionContext(tool.catalogManifest());
        RetainedSkillContext retained = new RetainedSkillContext();
        List<ModelMessage> history = history("redundant", input, content);
        instructions.prepareSystem("## UNRESTRICTED JAVASCRIPT GUIDANCE\n" + body, retained);

        List<ModelMessage> projection = instructions.refresh(history, retained);
        instructions.reconcile(projection, retained);

        assertEquals(content.modelText(), resultText(history.get(1)));
        assertFalse(result(history.get(1)).error());
        assertEquals(history.getFirst(), projection.getFirst());
        assertTrue(resultText(projection.get(1)).contains("state: already_loaded"));
        assertFalse(result(projection.get(1)).error());
        assertFalse(resultText(projection.get(1)).contains(body));
        assertEquals(1, retained.ranges().size());
        assertEquals(projection, instructions.refresh(projection, retained));
        assertReceipt(content, instructions.reuse(input, retained));
    }

    @Test
    void validationCacheStaysBoundedDropsDiscardedKeysAndChecksTheFullDocumentShape() {
        Fixture fixture = fixture();
        List<ModelMessage> history = flatten(fixture.chunks());
        RetainedSkillContext retained = new RetainedSkillContext();
        for (int turn = 0; turn < 100; turn++) {
            List<ModelMessage> projection = fixture.instructions().refresh(history, retained);
            fixture.instructions().reconcile(projection, retained);
            assertEquals(history, projection);
            assertEquals(3, retained.validationCount());
        }

        // Keep history strongly reachable. Removal must follow the projection, not wait for GC.
        fixture.instructions().reconcile(fixture.chunks().getLast().history(), retained);
        assertEquals(1, retained.validationCount());
        assertEquals(1, retained.ranges().size());
        assertEquals(6, history.size());
        fixture.instructions().reconcile(List.of(), retained);
        assertEquals(0, retained.validationCount());
        assertEquals(List.of(), retained.ranges());
        fixture.instructions().reconcile(history, retained);
        assertEquals(3, retained.validationCount());
        assertEquals(3, retained.ranges().size());

        // The same document key must not skip validation when the frozen chunk shape changes.
        SkillCatalogManifest original = fixture.tool().catalogManifest();
        SkillCatalogManifest.Document entry = original.documents().stream()
                .filter(document -> document.document().equals("SKILL.md")).findFirst().orElseThrow();
        List<SkillCatalogManifest.Chunk> differentChunks = entry.chunks().stream()
                .map(chunk -> new SkillCatalogManifest.Chunk(chunk.offset(), chunk.end(), "0".repeat(64)))
                .toList();
        SkillCatalogManifest.Document changed = new SkillCatalogManifest.Document(entry.name(), entry.document(),
                entry.source(), entry.fingerprint(), entry.length(), differentChunks,
                entry.availableReferences(), entry.description());
        SkillCatalogManifest altered = new SkillCatalogManifest(original.documents().stream()
                .map(document -> document.document().equals("SKILL.md") ? changed : document).toList());
        assertEquals(entry.key(), changed.key());
        assertNotEquals(entry, changed);
        SkillInstructionContext instructions = new SkillInstructionContext(altered);

        List<ModelMessage> projection = instructions.refresh(history, retained);
        instructions.reconcile(projection, retained);

        for (int index = 1; index < projection.size(); index += 2) assertInvalidated(projection.get(index));
        assertEquals(0, retained.validationCount());
        assertEquals(List.of(), retained.ranges());
        assertEquals(history, flatten(fixture.chunks()));
    }

    @Test
    void manifestRequiresCanonicalReferencesAnEntryAndExactCatalogShapeForEveryDocument() {
        Fixture fixture = fixture();
        SkillCatalogManifest manifest = fixture.tool().catalogManifest();
        SkillCatalogManifest.Document entry = manifest.documents().stream()
                .filter(document -> document.document().equals("SKILL.md")).findFirst().orElseThrow();
        List<List<String>> invalidLists = List.of(
                List.of("references/b.md", "references/a.md"),
                List.of("references/a.md", "references/a.md"),
                List.of("assets/a.md"),
                List.of("SKILL.md"),
                List.of("references/../a.md"),
                List.of("references/./a.md"),
                List.of("references/a.md\nforged metadata"),
                List.of("references/a.md\t"),
                List.of("references/a.md" + (char) 0),
                List.of("references/a.md" + (char) 127));
        for (List<String> references : invalidLists) {
            assertThrows(IllegalArgumentException.class, () -> withReferences(entry, references));
        }
        for (String document : List.of("assets/a.md", "references/../a.md", "references/./a.md",
                "references/a.md\n", "references/a.md" + (char) 127)) {
            assertThrows(IllegalArgumentException.class, () -> new SkillCatalogManifest.Document(
                    entry.name(), document, entry.source(), entry.fingerprint(), entry.length(),
                    entry.chunks(), entry.availableReferences(), entry.description()));
        }
        SkillCatalogManifest.Document reference = manifest.documents().stream()
                .filter(document -> document.document().equals("references/a.md")).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> new SkillCatalogManifest(List.of(entry, entry)));
        assertThrows(IllegalArgumentException.class, () -> new SkillCatalogManifest(List.of(reference)));
        assertThrows(IllegalArgumentException.class, () -> new SkillCatalogManifest(List.of(entry)));
        SkillCatalogManifest.Document undeclared = withReferences(entry, List.of());
        assertThrows(IllegalArgumentException.class, () -> new SkillCatalogManifest(manifest.documents().stream()
                .map(document -> document.document().equals("SKILL.md") ? undeclared : document).toList()));
        SkillCatalogManifest.Document disagrees = withReferences(reference, List.of("references/a.md"));
        assertThrows(IllegalArgumentException.class, () -> new SkillCatalogManifest(manifest.documents().stream()
                .map(document -> document.document().equals(reference.document()) ? disagrees : document).toList()));
        SkillCatalogManifest.Document otherSource = new SkillCatalogManifest.Document(reference.name(),
                reference.document(), "another_owner", reference.fingerprint(), reference.length(),
                reference.chunks(), reference.availableReferences(), reference.description());
        assertThrows(IllegalArgumentException.class, () -> new SkillCatalogManifest(manifest.documents().stream()
                .map(document -> document.document().equals(reference.document()) ? otherSource : document).toList()));
        assertEquals(manifest, new SkillCatalogManifest(manifest.documents()));
    }

    @Test
    void systemBodyHeadingsArePartOfTheExactDocumentNotSystemSectionBoundaries() {
        String name = SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT;
        String body = "First instruction.\n\n## BODY HEADING\nSecond instruction."
                + "\n\n## ANOTHER BODY HEADING\nFinal instruction.";
        SkillCatalogSnapshot catalog = repository(name, body, Map.of()).snapshot(Set.of()).forRequest(true);
        SkillInstructionContext instructions = new SkillInstructionContext(catalog);
        LoadSkillTool.Input input = new LoadSkillTool.Input(name);
        String marker = "## UNRESTRICTED JAVASCRIPT GUIDANCE\n";
        List<String> validSystems = List.of(marker + body, marker + body + "\n",
                "## CORE\nCore.\n\n" + marker + body + "\n\n## REAL NEXT SECTION\nNext.");
        for (String system : validSystems) {
            RetainedSkillContext retained = new RetainedSkillContext();
            instructions.prepareSystem(system, retained);
            instructions.reconcile(List.of(), retained);
            assertEquals(1, retained.ranges().size());
            assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, instructions.reuse(input, retained).state());
            assertTrue(instructions.manifest(retained).contains(name + " / SKILL.md: full"));
        }
        List<String> invalidSystems = List.of(marker + "First instruction.",
                marker + body.replace("Second instruction.", "Edited instruction."),
                marker + body + "extra text\n\n## REAL NEXT SECTION\nNext.");
        for (String system : invalidSystems) {
            RetainedSkillContext retained = new RetainedSkillContext();
            instructions.prepareSystem(system, retained);
            instructions.reconcile(List.of(), retained);
            assertEquals(List.of(), retained.ranges());
            assertNull(instructions.reuse(input, retained));
        }
    }

    @Test
    void capturedDocumentsKeepValueEqualityAndStablePrecomputedIdentities() {
        SkillSource source = source("pack", SkillSource.Origin.EXTERNAL, "guide", BODY,
                Map.of("references/a.md", "Reference bytes."));
        SkillParser parser = new SkillParser();
        SkillDocument first = parser.parse(source);
        SkillDocument equivalent = parser.parse(source);

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertEquals(first.metadata(), equivalent.metadata());
        assertEquals(first.instructions(), equivalent.instructions());
        assertEquals(first.references(), equivalent.references());
        assertEquals(first.documents(), equivalent.documents());
        assertEquals(3, first.documents().get("SKILL.md").chunks().size());
        assertEquals(LoadSkillTool.fingerprint(BODY), first.documents().get("SKILL.md").fingerprint());
        assertEquals(LoadSkillTool.fingerprint(BODY.substring(0, 8_192)),
                first.documents().get("SKILL.md").chunks().getFirst().fingerprint());
    }

    private static Fixture fixture() {
        SkillRepository repository = repository("guide", BODY,
                Map.of("references/a.md", "A reference.", "references/b.md", "B reference."));
        SkillCatalogSnapshot catalog = repository.snapshot(Set.of());
        LoadSkillTool tool = new LoadSkillTool(catalog).withOwner("client");
        List<Loaded> chunks = new ArrayList<>();
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        while (true) {
            LoadSkillTool.Output output = success(tool.invokeFresh(request("capture"), input));
            chunks.add(new Loaded(input, output, history("chunk-" + chunks.size(), input, output)));
            if (output.complete()) break;
            input = new LoadSkillTool.Input("guide", null, output.nextCursor());
        }
        assertEquals(3, chunks.size());
        return new Fixture(catalog, tool, new SkillInstructionContext(tool.catalogManifest()), List.copyOf(chunks));
    }

    private static List<ModelMessage> flatten(List<Loaded> chunks) {
        return chunks.stream().flatMap(chunk -> chunk.history().stream()).toList();
    }

    private static List<ModelMessage> history(
            String id, LoadSkillTool.Input input, LoadSkillTool.Output output) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("name", input.name());
        if (input.reference() != null) arguments.addProperty("reference", input.reference());
        if (input.cursor() != null) arguments.addProperty("cursor", input.cursor());
        return List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        id, "openallay__load_skill", arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        id, new JsonPrimitive(output.modelText()), false))));
    }

    private static ModelContent.ToolResult result(ModelMessage message) {
        return assertInstanceOf(ModelContent.ToolResult.class, message.content().getFirst());
    }

    private static String resultText(ModelMessage message) {
        return result(message).value().getAsString();
    }

    private static void assertInvalidated(ModelMessage message) {
        assertFalse(result(message).error());
        assertTrue(resultText(message).startsWith("skill_instructions: invalidated\n"));
    }

    private static void assertReceipt(LoadSkillTool.Output original, LoadSkillTool.Output receipt) {
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, receipt.state());
        assertEquals("", receipt.content());
        assertEquals(original.name(), receipt.name());
        assertEquals(original.document(), receipt.document());
        assertEquals(original.source(), receipt.source());
        assertEquals(original.fingerprint(), receipt.fingerprint());
        assertEquals(original.offset(), receipt.offset());
        assertEquals(original.nextOffset(), receipt.nextOffset());
        assertEquals(original.complete(), receipt.complete());
        assertEquals(original.nextCursor(), receipt.nextCursor());
    }

    private static SkillCatalogManifest.Document withReferences(
            SkillCatalogManifest.Document document, List<String> references) {
        return new SkillCatalogManifest.Document(document.name(), document.document(), document.source(),
                document.fingerprint(), document.length(), document.chunks(), references, document.description());
    }

    private static LoadSkillTool.Output copy(
            LoadSkillTool.Output output, String source, String content, String cursor) {
        return new LoadSkillTool.Output(output.name(), output.document(), source, output.fingerprint(),
                output.state(), content, output.offset(), output.nextOffset(), output.complete(), cursor,
                output.availableReferences(), output.allowedTools(), output.provenance());
    }

    private static ToolInvocationContext request(String correlation) {
        return ToolInvocationContext.developmentConsole(correlation);
    }

    private static ToolInvocationContext unrestrictedRequest(String correlation) {
        ToolInvocationContext regular = request(correlation);
        return new ToolInvocationContext(regular.correlationId(), regular.capturedAt(), regular.caller(),
                regular.player(), regular.registries(), regular.recipes(), regular.observableGameState(),
                regular.metrics(), true);
    }

    private static LoadSkillTool.Output success(ToolResult<LoadSkillTool.Output> result) {
        ToolResult.Success<?> success = assertInstanceOf(ToolResult.Success.class, result);
        return assertInstanceOf(LoadSkillTool.Output.class, success.value());
    }

    private static SkillRepository repository(String name, String body, Map<String, String> references) {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(repository.reload(List.of(source("pack", SkillSource.Origin.EXTERNAL,
                name, body, references)), Set.of()));
        return repository;
    }

    private static SkillSource source(String provenance, SkillSource.Origin origin,
            String name, String body, Map<String, String> references) {
        Map<String, String> files = new java.util.HashMap<>();
        files.put(name + "/SKILL.md", """
                ---
                name: %s
                description: Guide the player
                ---
                %s
                """.formatted(name, body));
        references.forEach((path, text) -> files.put(name + "/" + path, text));
        return new SkillSource(provenance, name + "/SKILL.md", files, origin);
    }

    private record Loaded(LoadSkillTool.Input input, LoadSkillTool.Output output, List<ModelMessage> history) {}
    private record Fixture(SkillCatalogSnapshot catalog, LoadSkillTool tool,
            SkillInstructionContext instructions, List<Loaded> chunks) {}
}
