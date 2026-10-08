package dev.openallay.model;

import com.google.gson.Gson;
import dev.openallay.model.anthropic.AnthropicMessagesClient;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.openai.OpenAiChatClient;
import java.util.Objects;

/** One protocol-neutral construction boundary shared by Guide and settings probes. */
public final class ProviderModelClients {
    private ProviderModelClients() {}

    public static ModelClient create(ModelConfig config, Gson gson) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(gson, "gson");
        {
dev.openallay.model.ModelClient $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((config.protocol())) {
case ANTHROPIC_MESSAGES:
{
$oaSwitch0_exit_result = new AnthropicMessagesClient(config, gson); break $oaSwitch0_exit;
}
case OPENAI_CHAT:
{
$oaSwitch0_exit_result = new OpenAiChatClient(config, gson); break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
    }
}
