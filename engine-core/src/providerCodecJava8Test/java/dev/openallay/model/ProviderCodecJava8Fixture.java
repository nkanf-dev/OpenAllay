package dev.openallay.model;

import com.google.gson.*;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.json.EngineJson;
import dev.openallay.model.anthropic.AnthropicJsonCodec;
import dev.openallay.model.config.*;
import dev.openallay.model.http.SseEvent;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import dev.openallay.model.openai.OpenAiJsonCodec;
import dev.openallay.model.tokenizer.ModelTokenEncoding;
import dev.openallay.util.Java8Collections;
import java.net.URI;
import java.time.Duration;
import java.util.*;

public final class ProviderCodecJava8Fixture {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void reject(Runnable run){try{run.run();throw new AssertionError("accepted invalid source");}catch(RuntimeException expected){checks++;}}
    private static ModelConfig config(ModelProtocol protocol){return new ModelConfig(true,protocol,URI.create("https://example.invalid/api"),"fixture-model",SecretValue.of("secret"),8192,1024,Duration.ofSeconds(1),Duration.ofSeconds(2),ModelReasoningEffort.AUTO,ModelTokenEncoding.AUTO,ModelImageCapabilityResolution.unknown());}
    public static void main(String[] args){
        int loggerChecks=dev.openallay.logging.LoggerJava8Fixture.run();
        int streamChecks=dev.openallay.model.anthropic.AnthropicStreamJava8Fixture.run()+dev.openallay.model.openai.OpenAiStreamJava8Fixture.run();
        Gson gson=EngineJson.create();AnthropicJsonCodec anthropic=new AnthropicJsonCodec(gson);OpenAiJsonCodec openai=new OpenAiJsonCodec(gson);
        JsonObject arguments=new JsonObject();arguments.addProperty("a",1);
        ModelContent.ToolUse use=new ModelContent.ToolUse("internal:call","fact",arguments);
        List<ModelMessage> messages=Java8Collections.listOf(new ModelMessage(ModelRole.ASSISTANT,Java8Collections.<ModelContent>listOf(use)),new ModelMessage(ModelRole.USER,Java8Collections.<ModelContent>listOf(new ModelContent.ToolResult("internal:call",new JsonPrimitive("result"),false))));
        ModelRequest request=new ModelRequest("system",messages,Collections.<ModelToolDefinition>emptyList(),true,"session");
        JsonObject ai=dev.openallay.json.JsonTrees.parse(openai.requestBody(config(ModelProtocol.OPENAI_CHAT),request)).getAsJsonObject();
        JsonObject an=dev.openallay.json.JsonTrees.parse(anthropic.requestBody(config(ModelProtocol.ANTHROPIC_MESSAGES),request)).getAsJsonObject();
        check(ai.get("model").getAsString().equals("fixture-model")&&ai.get("max_completion_tokens").getAsInt()==1024&&ai.get("stream").getAsBoolean(),"openai request fields");
        check(an.get("model").getAsString().equals("fixture-model")&&an.get("max_tokens").getAsInt()==1024,"anthropic request fields");
        ProviderToolIds ids=ProviderToolIds.forOpenAiChat(messages);check(ids.encode("internal:call").startsWith("call_")&&use.id().equals("internal:call"),"request wire identity not transcript mutation");
        List<ModelEvent> events=new ArrayList<ModelEvent>();ModelTurn first=anthropic.parseTurn("{\"model\":\"m\",\"stop_reason\":\"tool_use\",\"content\":[{\"type\":\"text\",\"text\":\"A\"},{\"type\":\"thinking\",\"thinking\":\"R\",\"signature\":\"sig\"},{\"type\":\"tool_use\",\"id\":\"a\",\"name\":\"fact\",\"input\":{}}],\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}",events::add);
        check(first.content().size()==3&&events.get(0) instanceof ModelEvent.TextDelta&&events.get(1) instanceof ModelEvent.ReasoningDelta&&events.get(2) instanceof ModelEvent.ToolUseComplete&&events.get(events.size()-1) instanceof ModelEvent.MessageComplete,"anthropic complete events");
        events.clear();ModelTurn second=openai.parseTurn("{\"model\":\"m\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"OK\",\"reasoning_content\":\"R\"}}],\"usage\":{\"prompt_tokens\":0,\"completion_tokens\":0}}",events::add);
        check(second.text().equals("OK")&&second.usage().inputKnown()&&second.usage().outputKnown(),"explicitzero usage");
        reject(() -> anthropic.parseTurn("{\"error\":{}}",e -> {}));reject(() -> anthropic.parseTurn("{\"model\":\"m\",\"stop_reason\":\"stop\",\"content\":[{\"type\":\"bad\"}]}",e -> {}));
        reject(() -> openai.parseTurn("{\"model\":\"m\",\"choices\":[]}",e -> {}));
        check(new ContextBudget(8192,1024).inputTokens()==6144,"actual output reservation");reject(() -> new ContextBudget(2048,1024));
        reject(() -> config(ModelProtocol.ANTHROPIC_MESSAGES).reasoningEffort().choices(null));
        check(ModelReasoningEffort.choices(ModelProtocol.OPENAI_CHAT).size()==8&&ModelReasoningEffort.choices(ModelProtocol.ANTHROPIC_MESSAGES).size()==6,"provider effort contract");
        reject(() -> ModelReasoningEffort.NONE.requireSupported(ModelProtocol.ANTHROPIC_MESSAGES));
        SseEvent value=new SseEvent("event","data");check(value.equals(new SseEvent("event","data"))&&value.hashCode()==new SseEvent("event","data").hashCode(),"SSE explicitvalue contract");
        if(checks+streamChecks+loggerChecks!=49)throw new AssertionError("Expected49 semantic checks, got "+(checks+streamChecks+loggerChecks));
        System.out.println("openai_request="+ai);System.out.println("anthropic_request="+an);System.out.println("anthropic_complete="+first);System.out.println("openai_complete="+second);System.out.println("sse="+value);
        System.out.println("checks="+(checks+streamChecks+loggerChecks));System.out.println("PASS actual provider codec/stream/config oracle");
    }
}
