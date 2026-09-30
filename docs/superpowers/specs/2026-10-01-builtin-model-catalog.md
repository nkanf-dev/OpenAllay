# Builtin model capability and price catalog

## Purpose

Known model names should work offline at arbitrary OpenAI-compatible endpoints even when `/models` publishes only IDs. The bundled table fills missing context by default with the deterministic best genuine match. It does not select a provider, change an endpoint or rewrite the configured model ID.

## Ownership and states

The release resource owns one immutable reviewed catalog generation. The existing loader owns required-context resolution. Settings owns an editable draft and a redacted matched-model projection. The runtime registry owns publication; active requests retain the runtime captured at submission.

Resource load transitions `unloaded -> ready` after full strict validation or `unloaded -> unavailable` with `builtin_catalog_invalid`/`builtin_catalog_unavailable`. Model changes transition `unmatched -> exact|normalized|similar` when eligible or remain `unmatched`. Blank-context resolution transitions to `resolved_explicit`, `resolved_trusted` or `resolved_builtin`; no eligible metadata gives `context_required`. Editing an autofilled context transitions to manual ownership. Save/reload retains existing atomic preparation/publication semantics; no active request changes.

## Precedence and matching

1. Explicit context/output always win, including a user-selected 1,000,000-token GPT-6 Luna budget. No 32K fallback or other arbitrary context cap exists.
2. Exact trusted provider cache context outranks bundled context. Cache keys and schema 1 stay unchanged. Pricing is not cache data.
3. Builtin exact canonical/upstream/alias matching precedes provider-wrapper and known nonidentity suffix normalization. Remaining close names require the same recognizable family and version, preserve meaningful variants, and exceed a documented similarity eligibility threshold.
4. Eligible BEST candidates rank by similarity, canonical/upstream/alias identity rank, then direct provider before known hosted sources, then canonical ID lexicographic. Resource ordering never changes the winner. Ties select one best candidate and expose it; no extra confirmation is mandatory.
5. Unknown/unrelated names never get a nearest arbitrary model. Blank unknown context stays required. Manual input works independently from catalog health.
6. Published output is shown only. The existing explicit profile output budget remains unchanged.

## Resource schema 1

Root exact fields: `schemaVersion`, `catalogVersion`, `publishedAt`, `sources`, `models`. Source exact fields: `id`, `label`, `url`, `capturedAt`. Model exact fields: `id`, `provider`, `family`, `aliases`, `contextWindowTokens`, `maxOutputTokens`, `pricing`, `capabilitySource`, `pricingSource`, `upstreamModelId`. Context must be positive; output is positive or null. Unknown context rows are omitted, not guessed. Every source reference resolves. Source URLs are public HTTPS evidence only; no runtime downloader consumes them.

Pricing is null or exact fields `currency: USD`, `unit: million_tokens`, `tiers`, `note`. Each tier has `minInputTokens`, `input`, `output`, `cacheRead`, `cacheWrite`. Rates are nonnegative decimal strings or null; null means unpublished, not free. Tiers start at zero and strictly increase. Notes retain restrictions such as time-of-day or nonstandard billing. All prices are published source estimates as of the snapshot, not claims about a configured gateway's charge. Malformed fields, duplicates, unsupported versions or invalid source references reject the whole resource; no partial import or migration.

## Presentation and update

Models settings shows the chosen model and match kind, context, published output, all published USD/1M tiers, source label/URL and capture time. Context is editable. Manual fields remain authoritative, including after changing match source. English and Simplified Chinese distinguish limits from charge estimates. No credentials or provider response bodies enter this view.

A developer explicitly fetches the fixed public models.dev/OpenRouter sources into a proposal artifact with source evidence/hashes. Fixture tests and the production strict decoder validate it. A reviewer inspects limits, aliases, price units/tiers and source changes before replacing the committed bundled resource. Users get updates with the release; runtime has no arbitrary-URL fetch or billable operation.

## Verification

Deterministic fixtures cover exact/alias/wrapped/suffixed/fuzzy names, version/family rejection, stable ties, unknowns, malformed schema, duplicate JSON fields, source provenance, null prices/output, redaction, explicit and trusted-cache overrides, strict cache rejection and captured-runtime immutability. Build verification waits for root's explicit Java25 wrapper slot.

Untouched automatic draft context remains `null`/omitted when saved. Save is not manual adoption. Reopening recomputes the effective context; later trusted metadata can replace builtin context. Only an actual context edit stores an explicit numeric value.

### Deterministic matching contract

Matching indexes all 1,121 entries without a row cap. Canonical and upstream IDs are exact identity candidates; explicit published aliases follow. Rank order is match class (exact, normalized, similar), similarity descending, identity rank (canonical ID, own upstream ID, alias), author/direct provider before the known hosted sources OpenRouter and Amazon Bedrock, then canonical ID lexicographic. Qualified canonical IDs therefore beat another provider's canonical alias. No ranking operation changes the configured request ID.

Normalization strips slash-delimited gateway/provider wrappers down to the final model segment. It folds case, whitespace/underscores/hyphens and dotted numeric version separators. The only stripped tails are `:free`, `:nitro`, `:floor`, `:online`, `-latest`, and a terminal full `20YYMMDD` / `20YY-MM-DD` date. Other tails, including preview, thinking, mini, nano, pro and custom gateway names, remain identity tokens. Similar matching requires the same exact recognizable family prefix, identical numeric version sequence and the same number of nonnumeric identity words. Different words require both lengths at least four and per-word similarity at least 0.75. Overall normalized Levenshtein similarity must be at least 0.82. This permits real close spellings such as `gpt-6-lunna` while rejecting missing/different variants, unrelated families and different versions. Ties use the order above, not resource order, and expose the chosen model/source.

Static candidate names, aliases, numbers and identity words are indexed once per immutable catalog. The most recent match is memoized. The screen caches its display projection by draft and effective resolution, so rendering does not repeatedly scan or normalize the table.
