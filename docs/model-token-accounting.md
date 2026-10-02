# Model token accounting

OpenAllay uses `com.knuddels:jtokkit:1.1.0` for offline BPE tokenization. It does not implement a custom tokenizer. The published JAR is pure Java, has no dependencies, targets Java 8, and works with the project's Java 25 runtime. It bundles the encoding resources. It does not fetch model files or load a native library at runtime.

Artifact SHA-256: `1501ce0259ab897c6746ccfafa1d208acd404fb17e1ac62e157172f2678b1183`. This is an external dependency fact, not an internal protocol version. The MIT license is bundled at `META-INF/licenses/jtokkit-MIT.txt`. Fabric includes this JAR. NeoForge nests it with JarJar. Both loaders must retain `com/knuddels/jtokkit/o200k_base.tiktoken` and `cl100k_base.tiktoken` in the nested artifact.

## Encoding selection

Model configuration and named profiles accept `tokenEncoding`:

- `auto` (default): use the published OpenAI model-to-encoding mapping when known. For unknown models, use the larger of JTokkit's `cl100k_base` and `o200k_base` counts.
- `cl100k_base`: explicitly select that stable encoding.
- `o200k_base`: explicitly select that stable encoding.

This choice does not change the endpoint, model name, configured context window, or output limit. A gateway alias does not establish an exact tokenizer. Anthropic's protocol does not imply either OpenAI encoding. AUTO therefore labels it `CONSERVATIVE_SURROGATE`. `EXPLICIT_ENCODING` records a user choice, not a claim that the provider uses it.

JTokkit 1.1.0's model registry predates GPT-4.1 and GPT-5. Its broad `gpt-4` prefix would select the wrong encoding for GPT-4.1/4.5. OpenAllay uses the published OpenAI mapping instead. It does not use JTokkit's historical context-window values. `gpt-oss` uses `o200k_harmony`, which the selected library does not implement; AUTO must not claim exact support.

## Counts and ownership

`estimateText` uses JTokkit's `countTokensOrdinary`. Ordinary mode treats special-token-looking strings as text. For a supported, selected encoding this is an exact plain-text BPE count.

Request estimates tokenize the provider-native text and framing projection. This covers roles, text, reasoning, tool-call IDs, JSON arguments, tool results, tool names/descriptions, schemas, and their serialized shape. Image blocks use an offline reference projection. Image bytes and Base64 are never fed to BPE. Image Token accounting remains unknown, so the UI labels mixed text/image estimates as text-only and does not show them as exact total occupancy. Model name, streaming flags and output limits are transport controls, not context content.

One endpoint estimator is shared by compaction, Agent admission/diagnostics, prompt/tool reservations, and local history accounting. Remote history preload uses a labelled surrogate until the server owns final admission.

A serialized JSON BPE count is **not** an exact provider request token count. Providers may apply hidden framing, rewrite tools, add a system prompt, or use an unpublished tokenizer. Taking the larger of two mature encodings is not a proven upper bound for a third tokenizer. OpenAllay does not add an invented percentage margin or claim universal chat-framing constants. Provider-reported usage remains authoritative after execution. A provider token-count service would require an explicit, controlled network capability; this implementation does not call one.

## Primary sources

- JTokkit [README](https://github.com/knuddelsgmbh/jtokkit/blob/main/README.md) and [MIT license](https://github.com/knuddelsgmbh/jtokkit/blob/main/LICENSE).
- Maven [release metadata](https://repo.maven.apache.org/maven2/com/knuddels/jtokkit/maven-metadata.xml), [POM](https://repo.maven.apache.org/maven2/com/knuddels/jtokkit/1.1.0/jtokkit-1.1.0.pom), [JVM target metadata](https://repo.maven.apache.org/maven2/com/knuddels/jtokkit/1.1.0/jtokkit-1.1.0.module), and [published source JAR](https://repo.maven.apache.org/maven2/com/knuddels/jtokkit/1.1.0/jtokkit-1.1.0-sources.jar).
- OpenAI [model encoding mapping](https://github.com/openai/tiktoken/blob/main/tiktoken/model.py) and [encoding resource hashes](https://github.com/openai/tiktoken/blob/main/tiktoken_ext/openai_public.py).
- OpenAI [token-count cookbook](https://cookbook.openai.com/examples/how_to_count_tokens_with_tiktoken) states that message/tool formulas are estimates and can change by model.
- Anthropic [tool system prompt](https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools#tool-use-system-prompt) and [token counting caveats](https://platform.claude.com/docs/en/build-with-claude/token-counting).

DJL's [HuggingFace tokenizers integration](https://github.com/deepjavalibrary/djl/blob/v0.38.0/extensions/tokenizers/README.md) can load local tokenizer assets for other models. Its current package adds JNI/native extraction, a larger dependency and platform matrix. It is not used merely to label an unknown gateway tokenizer as exact.
