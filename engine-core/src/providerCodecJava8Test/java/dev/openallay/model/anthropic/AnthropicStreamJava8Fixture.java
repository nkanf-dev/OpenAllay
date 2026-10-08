package dev.openallay.model.anthropic;

import dev.openallay.model.*;
import dev.openallay.model.http.SseEvent;
import java.util.*;

public final class AnthropicStreamJava8Fixture {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void reject(Runnable run){try{run.run();throw new AssertionError("accepted malformed stream");}catch(RuntimeException expected){checks++;}}
    private static void accept(AnthropicStreamAccumulator stream,String json){stream.accept(new SseEvent(null,json));}
    public static int run(){
        List<ModelEvent> events=new ArrayList<ModelEvent>(); AnthropicStreamAccumulator stream=new AnthropicStreamAccumulator(events::add);
        accept(stream,"{\"type\":\"message_start\",\"message\":{\"model\":\"claude-fixture\",\"usage\":{\"input_tokens\":100,\"cache_read_input_tokens\":50,\"cache_creation_input_tokens\":25,\"output_tokens\":0}}}");
        accept(stream,"{\"type\":\"content_block_start\",\"index\":2,\"content_block\":{\"type\":\"tool_use\",\"id\":\"call_a\",\"name\":\"fact\",\"input\":{}}}");
        accept(stream,"{\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"a\\\":\"}}");
        accept(stream,"{\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"1}\"}}");
        accept(stream,"{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"A\"}}");
        accept(stream,"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"B\"}}");
        accept(stream,"{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"R\"}}");
        accept(stream,"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"S\"}}");
        accept(stream,"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"signature_delta\",\"signature\":\"sig\"}}");
        accept(stream,"{\"type\":\"content_block_stop\",\"index\":2}");
        accept(stream,"{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":5}}");
        stream.accept(new SseEvent("ping","{}")); stream.accept(new SseEvent(null,"[DONE]"));
        ModelTurn turn=stream.finish();
        check(turn.text().equals("AB"),"text fragments");
        check(turn.content().get(0) instanceof ModelContent.Text && turn.content().get(1) instanceof ModelContent.Reasoning && turn.content().get(2) instanceof ModelContent.ToolUse,"index sorted content");
        ModelContent.Reasoning reasoning=(ModelContent.Reasoning)turn.content().get(1);check(reasoning.text().equals("RS")&&reasoning.signature().equals("sig"),"thinking/signature");
        check(turn.toolUses().get(0).input().get("a").getAsInt()==1,"fragmented actual JSON input");
        check(turn.usage().inputTokens()==175&&turn.usage().outputTokens()==5&&turn.usage().cacheReadTokens()==50&&turn.usage().cacheWriteTokens()==25&&turn.usage().complete(),"cumulative usage actualprecedence");
        check(events.get(0) instanceof ModelEvent.UsageUpdate&&events.get(1) instanceof ModelEvent.TextDelta&&events.get(2) instanceof ModelEvent.ReasoningDelta&&events.get(3) instanceof ModelEvent.ToolUseComplete,"live event order");
        check(events.get(events.size()-1) instanceof ModelEvent.MessageComplete,"terminal last");
        List<ModelEvent> partial=new ArrayList<ModelEvent>();AnthropicStreamAccumulator incomplete=new AnthropicStreamAccumulator(partial::add);
        accept(incomplete,"{\"type\":\"message_start\",\"message\":{\"model\":\"m\",\"usage\":{\"input_tokens\":1}}}");
        check(!((ModelEvent.UsageUpdate)partial.get(0)).usage().inputKnown(),"missingcache unknown");reject(incomplete::finish);
        reject(() -> accept(new AnthropicStreamAccumulator(e -> {}),"{\"type\":\"content_block_delta\",\"index\":9,\"delta\":{\"type\":\"text_delta\",\"text\":\"x\"}}"));
        reject(() -> accept(new AnthropicStreamAccumulator(e -> {}),"{\"type\":\"error\"}"));
        reject(() -> accept(new AnthropicStreamAccumulator(e -> {}),"{\"type\":\"unknown\"}"));
        System.out.println("anthropic_turn="+turn);System.out.println("anthropic_events="+events);return checks;
    }
}
