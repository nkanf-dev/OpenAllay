package dev.openallay.skill;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.json.EngineJson;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.requirement.RequirementCodec;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.tool.ToolResult;
import dev.openallay.util.Java8Collections;
import java.util.*;

/** Same actual-owner vectors on original modern and fully compiled genuine Java8 closures. */
public final class RetainedSkillJava8Fixture {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static void rejects(Runnable action, String label) {
        try { action.run(); throw new AssertionError("Accepted " + label); }
        catch (IllegalArgumentException | NullPointerException expected) { checks++; }
    }
    private static void immutable(Runnable action, String label) {
        try { action.run(); throw new AssertionError("Mutable " + label); }
        catch (UnsupportedOperationException expected) { checks++; }
    }
    private static LoadSkillTool.Output output(ToolResult<LoadSkillTool.Output> value) {
        check(value instanceof ToolResult.Success, "load must succeed");
        return ((ToolResult.Success<LoadSkillTool.Output>)value).value();
    }
    private static SkillDocument parse(String name, String body, String reference) {
        Map<String,String> files = new LinkedHashMap<String,String>();
        files.put("SKILL.md", "---\nname: "+name+"\ndescription: Evidence guide\nmetadata:\n  openallay/requires-skills: other\n---\n"+body+"\n");
        if (reference != null) files.put("references/a.md", reference);
        return new SkillParser().parsePackage("fixture",files,SkillSource.Origin.EXTERNAL);
    }
    private static SkillCatalogSnapshot catalog(SkillDocument document) {
        return new SkillCatalogSnapshot(Collections.singletonMap(document.metadata().name(),document));
    }
    private static List<ModelMessage> exchange(String id, LoadSkillTool.Input input, LoadSkillTool.Output output) {
        JsonObject json = new JsonObject(); json.addProperty("name",input.name());
        if (input.reference()!=null) json.addProperty("reference",input.reference());
        if (input.cursor()!=null) json.addProperty("cursor",input.cursor());
        return Java8Collections.listOf(new ModelMessage(ModelRole.ASSISTANT,
                Java8Collections.<ModelContent>listOf(new ModelContent.ToolUse(id,"openallay:load_skill",json))),
                new ModelMessage(ModelRole.USER, Java8Collections.<ModelContent>listOf(
                        new ModelContent.ToolResult(id,new JsonPrimitive(output.modelText()),false))));
    }
    private static void report(String label, Object value) {
        System.out.println(label+"="+value);
    }
    public static void main(String[] args) {
        check(LoadSkillTool.fingerprint("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"canonical sha256");
        SkillDocument document = parse("guide","Follow evidence.","Reference evidence.");
        SkillCatalogSnapshot snapshot = catalog(document);
        SkillCatalogManifest manifest = SkillCatalogManifest.capture(snapshot,"fixture");
        check(manifest.documents().size()==2,"manifest documents");
        check(document.metadata().requirements().skills().equals(Collections.singleton("other")),"real advisory parsing");
        immutable(() -> document.references().put("references/b.md","bad"),"reference snapshot");
        immutable(() -> manifest.documents().clear(),"manifest snapshot");
        immutable(() -> document.metadata().requiredMods().add("x"),"metadata snapshot");
        rejects(() -> parse("guide","\u2003",null),"unicode blank body");
        rejects(() -> SkillSource.normalize("../outside/SKILL.md"),"escaped source");
        rejects(() -> parse("Bad_Name","body",null),"invalid canonical name");
        Map<String,String> executable = new LinkedHashMap<String,String>();
        executable.put("SKILL.md","---\nname: guide\ndescription: safe\n---\nbody"); executable.put("scripts/x.js","bad");
        rejects(() -> new SkillParser().parsePackage("fixture",executable,SkillSource.Origin.EXTERNAL),"executable source");
        RequirementSet requirements = new RequirementSet(Collections.singleton("world:read"),Collections.singleton("demo:ext"),Collections.singleton("guide"));
        check(RequirementCodec.decode(RequirementCodec.encode(requirements)).equals(requirements),"real requirement codec");
        JsonObject duplicates = new JsonObject(); com.google.gson.JsonArray ids = new com.google.gson.JsonArray();
        ids.add("guide"); ids.add("guide"); duplicates.add("skills",ids);
        rejects(() -> RequirementCodec.decode(duplicates),"duplicate advisory");
        LoadSkillTool tool = new LoadSkillTool(snapshot,"fixture");
        ToolInvocationContext context = ToolInvocationContext.developmentConsole("request");
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output first = output(tool.invoke(context,input));
        LoadSkillTool.Output receipt = output(tool.invoke(context,input));
        check(first.state()==LoadSkillTool.LoadState.COMPLETE,"first plaintext");
        check(receipt.state()==LoadSkillTool.LoadState.ALREADY_LOADED,"same exchange admission");
        tool.closeRequestScope("request");
        check(output(tool.invoke(context,input)).state()==LoadSkillTool.LoadState.COMPLETE,"scope release");
        check(output(tool.invokeFresh(context,input)).state()==LoadSkillTool.LoadState.COMPLETE,"fresh ignores pending admission");
        List<ModelMessage> messages = exchange("read",input,first);
        SkillInstructionContext instructions = new SkillInstructionContext(manifest);
        RetainedSkillContext retained = new RetainedSkillContext();
        List<ModelMessage> refreshed = instructions.refresh(messages,retained);
        check(refreshed.get(0)==messages.get(0) && refreshed.get(1)==messages.get(1),"exact plaintext object identity");
        instructions.reconcile(messages,retained);
        check(retained.validationCount()==1,"weak identity validation cached");
        check(instructions.reuse(input,retained).state()==LoadSkillTool.LoadState.ALREADY_LOADED,"actual retained coverage");
        check(instructions.deliveredRanges(messages).get(0).content().equals("Follow evidence."),"exact delivered body");
        ModelContent.ToolUse use=(ModelContent.ToolUse)messages.get(0).content().get(0);
        ModelContent.ToolResult result=(ModelContent.ToolResult)messages.get(1).content().get(0);
        SkillCatalogManifest.Document entry=manifest.documents().stream().filter(d -> d.document().equals("SKILL.md")).findFirst().get();
        check(retained.validated(use,result,entry)!=null,"same result identity hit");
        check(retained.validated(new ModelContent.ToolUse(use.id(),use.name(),use.input()),result,entry)==null,"equal use clone is not identity");
        check(retained.validated(use,new ModelContent.ToolResult(result.toolUseId(),result.value(),result.error()),entry)==null,"equal result clone is not identity");
        instructions.reconcile(Collections.<ModelMessage>emptyList(),retained);
        check(retained.validationCount()==0 && retained.ranges().isEmpty(),"retired transcript releases validations");
        List<ModelMessage> receiptOnly=exchange("receipt",input,receipt);
        check(instructions.deliveredRanges(receiptOnly).isEmpty(),"receipt cannot invent coverage");
        check(((ModelContent.ToolResult)instructions.refresh(receiptOnly).get(1).content().get(0)).value().getAsString().startsWith("skill_instructions: invalidated"),"receipt alone invalidates");
        SkillInstructionContext changed=new SkillInstructionContext(catalog(parse("guide","New evidence.","Reference evidence.")));
        List<ModelMessage> invalidated=changed.refresh(messages);
        ModelContent.ToolResult historical=(ModelContent.ToolResult)invalidated.get(1).content().get(0);
        check(!historical.error() && historical.toolUseId().equals("read"),"historical success preserved");
        check(result.value().getAsString().contains("Follow evidence."),"original transcript unchanged");
        check(historical.value().getAsString().startsWith("skill_instructions: invalidated"),"changed fingerprint invalidates");
        String longBody=dev.openallay.util.Java8Strings.repeat("paragraph\n\n",2000);
        LoadSkillTool longTool=new LoadSkillTool(catalog(parse("guide",longBody,null)),"fixture");
        LoadSkillTool.Output chunk=output(longTool.invokeFresh(context,input));
        check(!chunk.complete() && !chunk.nextCursor().isEmpty(),"real partial chunk");
        LoadSkillTool.Input continuation=new LoadSkillTool.Input("guide",null,chunk.nextCursor());
        LoadSkillTool.Output next=output(longTool.invokeFresh(context,continuation));
        check(next.offset()==chunk.nextOffset(),"opaque cursor contiguous range");
        check(LoadSkillTool.decodeCursor(LoadSkillTool.encodeCursor("guide","SKILL.md","fixture",first.fingerprint(),8),"guide","SKILL.md","fixture",first.fingerprint())==8,"cursor exact roundtrip");
        rejects(() -> LoadSkillTool.decodeCursor(chunk.nextCursor(),"other","SKILL.md",chunk.source(),chunk.fingerprint()),"cursor source admission");
        String supplementary=dev.openallay.util.Java8Strings.repeat("x",8191)+"🐝z";
        check(LoadSkillTool.chunkEnd(supplementary,0)==8191,"surrogate boundary retained");
        RetainedSkillContext.Key key=new RetainedSkillContext.Key("guide","SKILL.md","fixture",first.fingerprint());
        List<RetainedSkillContext.Range> ranges=new ArrayList<RetainedSkillContext.Range>();
        int missing=1024;
        for(int i=2047;i>=0;i--){if(i==missing)continue;ranges.add(new RetainedSkillContext.Range(key,i*4,i*4+2,8192));ranges.add(new RetainedSkillContext.Range(key,i*4+1,i*4+4,8192));}
        RetainedSkillContext.Coverage coverage=new RetainedSkillContext.Coverage(ranges);
        boolean exactIntervals = true;
        for(int i=0;i<2048;i++) exactIntervals &= coverage.contains(key,i*4,i*4+4)==(i!=missing);
        check(exactIntervals,"all2048 out of order interval coverage checks");
        check(!coverage.contains(key,0,8192),"never fill missing interval");
        coverage.add(new RetainedSkillContext.Range(key,missing*4,missing*4+4,8192)); check(coverage.contains(key,0,8192),"merge actual gap bridge");
        rejects(() -> new RetainedSkillContext.Range(key,1,0,4),"range validation");
        check(key.equals(new RetainedSkillContext.Key("guide","SKILL.md","fixture",first.fingerprint())) && key.hashCode()==new RetainedSkillContext.Key("guide","SKILL.md","fixture",first.fingerprint()).hashCode(),"explicit key equality/hash");
        com.google.gson.Gson json=EngineJson.create();
        check(json.fromJson(json.toJson(input),LoadSkillTool.Input.class).equals(input),"actual schema input roundtrip");
        check(json.fromJson(json.toJson(first),LoadSkillTool.Output.class).equals(first),"actual schema output roundtrip");
        check(json.fromJson(json.toJson(manifest),SkillCatalogManifest.class).equals(manifest),"actual schema nested manifest roundtrip");
        check(tool.descriptor().id().equals("openallay:load_skill"),"canonical tool descriptor");
        check(first.availableReferences().equals(Collections.singletonList("references/a.md")),"exact declared references");
        check(first.offset()==0 && first.nextOffset()==first.content().length(),"exact complete bounds");
        check(LoadSkillTool.decodeCursor("","guide","SKILL.md",first.source(),first.fingerprint())==0,"empty cursor first range");
        if(checks!=52) throw new AssertionError("Expected exactly52 fixture checks, got "+checks);
        report("input",input); report("output",first); report("manifest",manifest);
        report("model_text",first.modelText().replace("\n","\\n")); report("checks",checks);
        System.out.println("PASS actual retained skill context oracle");
    }
}
