package dev.openallay.model.openai;

import dev.openallay.model.*;
import dev.openallay.model.http.SseEvent;
import java.util.*;

public final class OpenAiStreamJava8Fixture {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void reject(Runnable run){try{run.run();throw new AssertionError("accepted malformed stream");}catch(RuntimeException expected){checks++;}}
    private static void accept(OpenAiStreamAccumulator stream,String json){stream.accept(new SseEvent(null,json));}
    public static int run(){
        List<ModelEvent> events=new ArrayList<ModelEvent>();OpenAiStreamAccumulator stream=new OpenAiStreamAccumulator(events::add);
        accept(stream,"{\"model\":\"openai-fixture\",\"choices\":[{\"delta\":{\"content\":\"A\",\"reasoning_content\":\"R\",\"tool_calls\":[{\"index\":0,\"id\":\"call_a\",\"function\":{\"name\":\"fa\",\"arguments\":\"{\\\"a\\\":\"}}]}}]}");
        accept(stream,"{\"choices\":[{\"finish_reason\":\"tool_calls\",\"delta\":{\"content\":\"B\",\"reasoning_content\":\"S\",\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"ct\",\"arguments\":\"1}\"}}]}}],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":2,\"prompt_tokens_details\":{\"cached_tokens\":3}}}");
        stream.accept(new SseEvent(null,"[DONE]"));ModelTurn turn=stream.finish();
        check(turn.text().equals("AB"),"text fragments");check(((ModelContent.Reasoning)turn.content().get(1)).text().equals("RS"),"reasoning fragments");
        check(turn.toolUses().get(0).name().equals("fact")&&turn.toolUses().get(0).input().get("a").getAsInt()==1,"tool name/json fragment aggregation");
        check(turn.usage().inputTokens()==9&&turn.usage().outputTokens()==2&&turn.usage().cacheReadTokens()==3&&turn.usage().uncachedInputTokens()==6,"cachedusage precedence");
        check(events.get(0) instanceof ModelEvent.TextDelta&&events.get(1) instanceof ModelEvent.ReasoningDelta&&events.get(2) instanceof ModelEvent.UsageUpdate,"usage before chunk text event order");
        check(events.get(events.size()-1) instanceof ModelEvent.MessageComplete,"terminal last");
        for(String usage:new String[]{"null","{}","{\"prompt_tokens\":3}","{\"prompt_tokens\":null,\"completion_tokens\":1}","{\"prompt_tokens\":0,\"completion_tokens\":0}"}){
            List<ModelEvent> observed=new ArrayList<ModelEvent>();OpenAiStreamAccumulator next=new OpenAiStreamAccumulator(observed::add);
            accept(next,"{\"model\":\"m\",\"usage\":"+usage+",\"choices\":[{\"finish_reason\":\"stop\",\"delta\":{\"content\":\"OK\",\"tool_calls\":null}}]}");
            ModelTurn result=next.finish();check(result.text().equals("OK")&&observed.stream().anyMatch(ModelEvent.UsageUpdate.class::isInstance)!=usage.equals("null"),"optionalusage observation");
        }
        reject(() -> accept(new OpenAiStreamAccumulator(e -> {}),"{\"model\":\"m\",\"usage\":[],\"choices\":[]}"));
        reject(() -> accept(new OpenAiStreamAccumulator(e -> {}),"{\"model\":\"m\",\"usage\":{\"prompt_tokens_details\":[]},\"choices\":[]}"));
        reject(() -> new OpenAiStreamAccumulator(e -> {}).finish());
        OpenAiStreamAccumulator malformed=new OpenAiStreamAccumulator(e -> {});accept(malformed,"{\"model\":\"m\",\"choices\":[{\"finish_reason\":\"tool_calls\",\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"x\",\"function\":{\"name\":\"fact\",\"arguments\":\"[]\"}}]}}]}");reject(malformed::finish);
        System.out.println("openai_turn="+turn);System.out.println("openai_events="+events);return checks;
    }
}
