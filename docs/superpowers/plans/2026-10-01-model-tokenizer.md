# Model tokenizer implementation plan

Goal: Replace byte-as-token context accounting with mature Java BPE counts. Keep actual endpoint, model and context budgets unchanged.

1. Use JTokkit published artifact and bundled encodings. Confirm license, API, resource packaging, current model-mapping limitations against primary sources.
2. Add stable encoding selection and estimator metadata. Preserve a single shared estimator per endpoint. Unknown models and Anthropic use an explicitly labelled conservative BPE surrogate; no provider calls.
3. Share provider codec context JSON projection with request encoding. Count ordinary content with JTokkit and budget the native JSON shape/envelope separately.
4. Wire client/server runtime, named model configuration and history accounting. Let the context worker wire Agent/Compactor ownership and GetUnits to the same interface.
5. Replace obsolete byte-based estimator tests with BPE vectors, multilingual/JSON/tool/framing/unknown-profile tests. Add bundled dependency on both loaders.
6. Do not run Gradle until root authorizes the shared verification gate. Do not read live configuration, credentials or environment. Do not commit.
