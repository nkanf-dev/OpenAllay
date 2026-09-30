# Built-in model catalog source verification

This is source evidence and a repeatable development refresh procedure. It is
not a claim that an arbitrary gateway supports a listed model or charges these
prices. Runtime use of the bundled table does not require a network request.

## Snapshot

The proposal was normalized from three unauthenticated public GET responses:

| Source | Completed capture, UTC | Raw rows | Normalized rows |
| --- | --- | --- | --- |
| `https://models.dev/api.json` | 2026-09-30T16:22:43Z | 225 providers | 465 selected provider rows |
| `https://models.dev/models.json` | 2026-09-30T16:26:28Z | 434 canonical models | 194 additional canonical rows |
| `https://openrouter.ai/api/v1/models` | 2026-09-30T16:22:42Z | 464 models | 462 rows |

The normalized proposal contains **1,121 records**. It has 200 unknown pricing
objects, 16 unknown maximum-output limits, and 132 records with multiple context
price tiers. There is no row-count or search-result cap in the normalizer.

`source-snapshot-2026-09-30.json` records source URLs, capture times, SHA-256
hashes, counts, selected official corroboration, and unavailable official URLs.
Raw catalogs and HTML are not checked in. The proposal is not duplicated here;
the feature lead reviews it before copying it to the production resource.

## Selection and identity

The provider catalog contributes text-generation models from the selected APIs
listed in `DIRECT_PROVIDERS` in the updater. This includes author APIs and
Bedrock's distinct hosting IDs; Bedrock prices and limits are not author-API
prices and limits. The canonical catalog contributes
all additional published text-generation identities. It includes older Claude
and widely used open weights such as Llama, Gemma, Qwen, gpt-oss, GLM, Kimi,
MiniMax, Mistral, and Nemotron. OpenRouter contributes its full published list.

Models must publish positive context limits and accept and produce text. Missing
or zero context is not guessed. Specialized embedding, reranking, transcription,
image, audio-only, and video models are excluded. Some legacy upstream metadata
marks embeddings as `text` output; explicit specialized names are still rejected.
The two excluded OpenRouter rows in this snapshot are the Lyria music models.

Identity is source-provider scoped:

- Direct: `openai/gpt-6-luna`, `anthropic/claude-sonnet-5-5`, etc.
- OpenRouter: `openrouter/openai/gpt-6-luna`; its `upstreamModelId` stays
  `openai/gpt-6-luna`.
- Canonical-only: the published author/model identity. Its hosting price is null.

Aliases use only upstream IDs, display names, explicitly published canonical IDs,
canonical slugs, and Hugging Face IDs. No guessed date alias is created. Duplicate
aliases are removed without regard to case. Direct capability rows win only when
an additional canonical row has the same identity; distinct serving providers
stay distinct.

## Units, limits, and unknown values

The upstream Models.dev README documents `cost.input`, `output`, `cache_read`,
and `cache_write` as USD per million tokens. Its provider rows can override
canonical model capabilities. Its canonical API is provider-independent and does
not establish a hosting charge. See:

- <https://raw.githubusercontent.com/anomalyco/models.dev/dev/README.md>
- <https://models.dev>

The OpenRouter schema documents prompt, completion, and input cache prices as USD
per token. The updater uses exact decimal arithmetic and multiplies these rates
by 1,000,000. It preserves cache-write separately; OpenRouter describes the default
cache-write rate as the five-minute TTL where providers publish several TTLs.
See <https://openrouter.ai/docs/api-reference/models/get-models>.

The bundled pricing unit is always `million_tokens` with currency `USD`. Prices
are decimal strings, not binary floating-point values. Missing prices and
OpenRouter's dynamic-route `-1` sentinel become explicit nulls. An explicitly
published zero remains zero. Missing maximum output stays null. A maximum output
limit is a model capability, not an instruction to overwrite a user's configured
output reserve.

Context-dependent prices retain the exact source threshold. The first tier is
zero. Later tiers are strictly sorted. This snapshot includes the actual 272,000
input-token boundary for GPT-6 Luna, not an invented 200,000 boundary copied from
a legacy upstream field name. Boundary inclusivity follows upstream terms; the
normalizer does not infer it from a generic field name.

Some OpenRouter records publish time-dependent overrides. Their conditions and
USD-per-million rates are retained in the required pricing note. They are not
misrepresented as context tiers. The captured official DeepSeek schedule is
included only for the three named Flash aliases whose base rates match the
verified off-peak values. Pro catalog rates differ from the captured official
page, so the updater does not attach that page's schedule to Pro.

A pricing note contains only material published rate conditions. Named service
modes include their specific published prices when present. Rates already
represented by context tiers need no duplicate prose. Ordinary records have an
empty note. Unknown prices remain null without an extra warning paragraph.

## Public GPT-6 Luna evidence

Both fetched catalogs actually publish GPT-6 Luna as of this snapshot:

- Models.dev provider ID `openai`, model ID `gpt-6-luna`.
- OpenRouter model ID `openai/gpt-6-luna`.
- Published context: 1,050,000 tokens. Published maximum output: 128,000 tokens.
- Base USD per million: input `0.1`, output `0.5`, cached read `0.01`, cache write
  `0.125`.
- Published 272,000-token context tier: input `0.2`, output `0.75`, cached read
  `0.02`, cache write `0.25`.

These are catalog claims with exact public source provenance. The official OpenAI
API model, pricing, and announcement URLs returned HTTP 403 during this research.
They are **not** recorded as successfully verified official pages. No price is
inferred for OMP or another private/custom gateway. The user's explicit
1,000,000-token budget remains a separate override and takes priority over the
reference table.

## Official provider corroboration

The fetched official pages corroborate selected current values, not every record:

- Claude models overview: current Fable 5.1, Opus 5.5, and Sonnet 5.5 publish 1M
  context and 128K output. Their base input/output USD per MTok are 10/50, 4/20,
  and 2/10. Haiku 4.5 publishes 200K/64K and 1/5.
- DeepSeek pricing: Flash and Pro publish 1M context and 384K output; off-peak and
  peak rates differ. Legacy Flash model IDs remain accepted aliases. The official
  page gives the schedule and exceptions.
- xAI models: Grok 4.7 publishes 500K context, input $2/1M, and output $6/1M.
- Google and Alibaba official models pages corroborate their current model
  lineups. The bundled limits and prices remain explicitly catalog sourced.

Exact URLs and capture times are in the source evidence JSON. The unit facts from
OpenRouter docs and Models.dev's README are public and require no API key.

## Development refresh

Run fixture tests first. They do not access any provider:

```text
python3 -m unittest discover -s scripts -p test_update_builtin_model_catalog.py -v
python3 -m py_compile scripts/update-builtin-model-catalog.py scripts/test_update_builtin_model_catalog.py
```

For an opt-in public refresh:

```text
python3 scripts/update-builtin-model-catalog.py --fetch-public --output /tmp/builtin-model-catalog.proposal.json
```

This command reads only the three fixed allowlisted public catalog URLs. It sends
no authorization header, reads no model configuration, calls no inference
endpoint, and saves no raw response into the repository. Redirects fail until the
allowlist is reviewed. Sources complete independently; each captures its actual
completion timestamp. Failure or a partial OpenRouter response leaves an existing
output file unchanged.

For an offline reproducible refresh, save the three public responses outside the
repository and record their real capture times:

```text
python3 scripts/update-builtin-model-catalog.py --models-dev /tmp/models-dev.json --models-dev-canonical /tmp/models-dev-canonical.json --openrouter /tmp/openrouter-models.json --captured-at 2026-09-30T16:22:43Z --openrouter-captured-at 2026-09-30T16:22:42Z --models-dev-canonical-captured-at 2026-09-30T16:26:28Z --output /tmp/builtin-model-catalog.proposal.json
```

Review model identities, changes in source shapes, limits, prices, nulls, and
source terms before replacing the bundled resource. Repeat this developer
refresh when preparing releases or when public catalogs change. No runtime
refresh, billable model test, build, or game launch is part of this tool.

## Verification result

- 15 offline fixture tests passed. They cover units, context tiers, unknown
  prices, canonical-only null pricing, case-insensitive aliases, positive limits,
  specialized exclusions, source times, pagination failures, exact schemas,
  redirects, opt-in network behavior, and a 1,105-row synthetic uncapped list.
- The note-only cleanup re-normalized the same saved responses. All 1,121 model
  identities, aliases, limits, prices, tiers, source IDs, and capture times stayed
  unchanged. Only 921 pricing notes changed; 20 material rate-condition notes
  remain. No new network request was used.
- Python compile check passed.
- The native CLI's `--fetch-public` path completed three unauthenticated public
  requests and produced 1,121 records. This is an interface check, not a provider
  or inference test. Its later response timestamp is not substituted into the
  reviewed snapshot's source captures.

## Attribution

Models.dev is community-maintained data, not a manufacturer guarantee. The
Models.dev repository publishes the MIT license, copyright 2025 models.dev.
`LICENSE.models.dev` preserves that copyright and permission notice for the
normalized source-derived data. The feature should preserve this notice when
shipping the bundled data. No upstream model descriptions, secrets, raw catalogs,
or authenticated response contents are included. OpenRouter rates and limits
remain explicitly attributed to its public catalog and source terms.
