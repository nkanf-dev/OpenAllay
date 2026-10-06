package dev.openallay.trace.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.openallay.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ToolArgumentCodecTest {
    record Input(String name, String reference, int count) {}

    @Test
    void declaredStringsRejectGsonCoercionWhileNullAndOtherTypedFieldsRemainCompatible() {
        ToolArgumentCodec codec = new ToolArgumentCodec(dev.openallay.json.EngineJson.create());
        for (String field : List.of("name", "reference")) {
            for (String malformed : List.of("42", "false", "{}", "[]")) {
                var arguments = dev.openallay.json.JsonTrees.parse("{\"name\":\"skill\",\"reference\":\"references/a.md\",\"count\":3}")
                        .getAsJsonObject();
                arguments.add(field, dev.openallay.json.JsonTrees.parse(malformed));
                ToolResult.Failure<Input> failure = assertInstanceOf(
                        ToolResult.Failure.class, codec.decode(arguments, Input.class));
                assertEquals("invalid_arguments", failure.code());
            }
        }
        ToolResult.Success<Input> success = assertInstanceOf(ToolResult.Success.class, codec.decode(
                dev.openallay.json.JsonTrees.parse("{\"name\":\"skill\",\"reference\":null,\"count\":3}").getAsJsonObject(),
                Input.class));
        assertEquals(new Input("skill", null, 3), success.value());
    }
}
