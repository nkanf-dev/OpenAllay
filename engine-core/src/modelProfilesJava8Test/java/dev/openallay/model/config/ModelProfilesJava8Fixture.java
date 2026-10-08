package dev.openallay.model.config;

import com.google.gson.*;
import dev.openallay.json.EngineJson;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.tokenizer.ModelTokenEncoding;
import dev.openallay.tool.ToolResult;
import dev.openallay.util.Java8Collections;
import java.io.StringReader;
import java.net.URI;
import java.time.*;
import java.util.*;

public final class ModelProfilesJava8Fixture {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void reject(Runnable action,String label){try{action.run();throw new AssertionError("accepted "+label);}catch(RuntimeException expected){checks++;}}
    private static ModelProfilesConfigLoader.Load load(ModelProfilesConfigLoader loader,String json,Map<String,String> environment){ToolResult<ModelProfilesConfigLoader.Load> result=loader.load(new StringReader(json),environment);check(result instanceof ToolResult.Success,"strict valid load");return ((ToolResult.Success<ModelProfilesConfigLoader.Load>)result).value();}
    private static void failure(ModelProfilesConfigLoader loader,String json){check(loader.load(new StringReader(json),Collections.<String,String>emptyMap()) instanceof ToolResult.Failure,"strict malformed load");}
    private static ModelProfileDefinition profile(String id,boolean enabled,Integer context,Integer output,Duration timeout){return new ModelProfileDefinition(id,id,enabled,ModelProtocol.OPENAI_CHAT,URI.create("https://example.invalid/api"),"fixture-model","MODEL_KEY",context,output,timeout,Duration.ofSeconds(30),null,ModelReasoningEffort.AUTO,ModelTokenEncoding.AUTO,ImageInputCapability.SUPPORTED);}
    public static void main(String[] args){
        BuiltinModelCatalog catalog=BuiltinModelCatalog.bundled().catalog();check(catalog!=null,"realbundled catalog loaded");
        ModelProfilesConfigLoader loader=new ModelProfilesConfigLoader(catalog);ModelProfilesConfigWriter writer=new ModelProfilesConfigWriter();
        ModelProfileDefinition enabled=profile("enabled",true,8192,1024,Duration.ofSeconds(5));ModelProfileDefinition disabled=profile("disabled",false,null,null,Duration.ofSeconds(5));
        ModelProfilesConfig config=new ModelProfilesConfig("enabled",Java8Collections.listOf(enabled,disabled));String encoded=writer.encode(config);Map<String,String> env=Collections.singletonMap("MODEL_KEY","fixture-secret");
        ModelProfilesConfigLoader.Load first=load(loader,encoded,env);
        check(first.config().equals(config)&&first.profiles().size()==2,"ordered definitions survive");check(first.profiles().get(0).available()&&!first.profiles().get(1).available(),"enableddisabled retained");
        check(first.profiles().get(1).failure().code().equals("model_disabled")&&first.profiles().get(1).diagnosticView().contextWindowTokens()==null,"disabledunknown diagnostic");
        check(enabled.credentialRef().equals("env:MODEL_KEY")&&enabled.baseUri().toString().endsWith("/"),"qualifiedcredential/base normalization");
        check(first.profiles().get(0).runtimeConfig().apiKey().reveal().equals("fixture-secret"),"actual credential resolution");
        check(!encoded.contains("fixture-secret")&&!encoded.contains("\"apiKey\"")&&!first.profiles().get(0).toString().contains("fixture-secret"),"credentialfree serialization/redaction");
        check(encoded.equals(writer.encode(first.config())),"writer canonical roundtrip");
        JsonObject json=dev.openallay.json.JsonTrees.parse(encoded).getAsJsonObject();JsonObject omitted=json.getAsJsonArray("profiles").get(1).getAsJsonObject();
        check(!omitted.has("contextWindowTokens")&&!omitted.has("maxOutputTokens")&&!omitted.has("metadata")&&!omitted.has("reasoningEffort"),"nullable/default fields omitted");
        ModelProfilesConfigLoader.Load missing=load(loader,encoded,Collections.<String,String>emptyMap());check(missing.profiles().get(0).failure().code().equals("model_not_configured"),"unresolvedcredential retains definition");
        String[] invalid={"[]",encoded.replace("\"defaultProfileId\":\"enabled\"","\"defaultProfileId\":\"missing\""),encoded.replace("\"enabled\":true","\"enabled\":\"true\""),encoded.replace("\"contextWindowTokens\":8192","\"contextWindowTokens\":8192.5"),encoded.replace("\"protocol\":\"openai_chat\"","\"protocol\":\"invented\""),encoded.replace("\"requestTimeoutSeconds\":30","\"requestTimeoutSeconds\":0"),encoded.replace("\"baseUrl\":\"https://example.invalid/api/\"","\"baseUrl\":\"http://remote.invalid/api/\""),encoded.replace("\"model\":\"fixture-model\"","\"model\":\"\\u2003\""),"{\"defaultProfileId\":\"enabled\",\"defaultProfileId\":\"disabled\",\"profiles\":[]}"};
        for(String bad:invalid)failure(loader,bad);
        JsonObject extra=dev.openallay.json.JsonTrees.parse(encoded).getAsJsonObject();extra.addProperty("unknown",true);failure(loader,extra.toString());
        reject(() -> new ModelProfilesConfig("missing",Collections.singletonList(enabled)),"default absent");reject(() -> new ModelProfilesConfig("enabled",Java8Collections.listOf(enabled,enabled)),"duplicate ids");
        reject(() -> profile("invalid id",true,8192,1024,Duration.ofSeconds(1)),"id syntax");reject(() -> profile("bad",true,2048,1024,Duration.ofSeconds(1)),"budget reserves");
        reject(() -> writer.encode(new ModelProfilesConfig("enabled",Collections.singletonList(profile("enabled",true,8192,1024,Duration.ofMillis(1500))))),"fractional timeout writer");
        for(ModelReasoningEffort effort:ModelReasoningEffort.values()){
            ModelProfileDefinition varied=new ModelProfileDefinition("effort","Effort",true,ModelProtocol.OPENAI_CHAT,enabled.baseUri(),enabled.model(),enabled.credentialRef(),8192,1024,enabled.connectTimeout(),enabled.requestTimeout(),null,effort,ModelTokenEncoding.CL100K_BASE,ImageInputCapability.UNSUPPORTED);
            String serialized=writer.encode(new ModelProfilesConfig("effort",Collections.singletonList(varied)));ModelProfilesConfigLoader.Load read=load(loader,serialized,env);
            check(read.config().profiles().get(0).equals(varied)&&read.profiles().get(0).runtimeConfig().reasoningEffort()==effort,"efforttokenimage roundtrip");
        }
        ModelProfileDefinition.MetadataProvenance provenance=new ModelProfileDefinition.MetadataProvenance("openrouter","vendor/model",Instant.parse("2026-01-01T00:00:00Z"));
        ModelProfileDefinition annotated=new ModelProfileDefinition("main","Main",true,enabled.protocol(),enabled.baseUri(),enabled.model(),enabled.credentialRef(),8192,1024,enabled.connectTimeout(),enabled.requestTimeout(),provenance);
        ModelProfilesConfigLoader.Load annotatedRead=load(loader,writer.encode(new ModelProfilesConfig("main",Collections.singletonList(annotated))),env);check(annotatedRead.config().profiles().get(0).metadata().equals(provenance),"metadata provenance serialization");
        Gson gson=EngineJson.create();check(gson.fromJson(gson.toJson(provenance),ModelProfileDefinition.MetadataProvenance.class).equals(provenance),"actual provenance valueschema timestamp");
        ModelConfigLoader legacy=new ModelConfigLoader();String legacyJson="{\"baseUrl\":\"https://example.invalid/api\",\"model\":\"fixture-model\",\"protocol\":\"openai_chat\",\"apiKey\":\"json-key\",\"contextWindowTokens\":8192,\"maxOutputTokens\":1024}";
        ToolResult<ModelConfig> legacyResult=legacy.load(new StringReader(legacyJson),Collections.singletonMap("OPENALLAY_API_KEY","env-key"));check(legacyResult instanceof ToolResult.Success,"real legacyconfig load");check(((ToolResult.Success<ModelConfig>)legacyResult).value().apiKey().reveal().equals("env-key"),"environment credential precedence");
        check(legacy.load(new StringReader(legacyJson.replace("\"maxOutputTokens\":1024","\"maxOutputTokens\":1024.5")),env) instanceof ToolResult.Failure,"legacy fraction rejects");
        if(checks!=49)throw new AssertionError("Expected49 profile checks, got "+checks);
        System.out.println("encoded="+encoded.replace("\n","\\n"));System.out.println("config="+config);System.out.println("resolved_diagnostic="+first.profiles().get(0).diagnosticView());
        System.out.println("checks="+checks);System.out.println("PASS complete profile/config loader writer oracle");
    }
}
