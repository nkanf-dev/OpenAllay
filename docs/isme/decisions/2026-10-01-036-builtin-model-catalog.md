# SKMB-2026-10-01-036: Builtin Model Catalog and Automatic BEST Matching

- status: accepted
- decided_by: designer
- approval_source: exact user request: “我们这个项目内部应该内置一个表，因为 models 它可能返回不了那个上下文的数量，我们应该预设一个表，我们可能有时候去更新一下那个表，就是我们到处抓取，我们去把默认，把目前就是比较广泛的一些模型，它们的定价，它们的上下文的东西，我们去给它拿下来，就是后续我们就去匹配，模糊匹配，这里可以走模糊匹配的路子，就是默认选最匹配的那一个，然后去给它自动填好它的上下文，这是一个产品体验的东西，我们单独给它作为一个提交去做”
- additional_approval: “对于 GPT 6 Luna 来说，我们可以给到 1M 的预算”
- date: 2026-10-01
- commit: pending
- patterns: A_async_wait, B_state_persistence, C_concurrent_operations, D_external_dependency, F_fail_semantics
- scope: offline builtin model capability/price table, automatic missing-context resolution and editable settings estimates

## Decision

OpenAllay bundles a reviewed updatable table of published model context/output limits and reference prices. The existing common metadata/configuration route automatically fills missing context by the deterministic BEST genuine match. Exact canonical/alias identity precedes provider-wrapper normalization, then family/version-aware similarity. Eligible ties resolve by similarity, canonical/upstream/alias identity rank, direct-provider priority, then canonical-ID lexicographic order. The chosen match and published source are visible and editable without a mandatory extra confirmation. Unrelated unknown names remain manual context required; no fake 32K or arbitrary context-budget cap is permitted.

Manual values remain authoritative. Explicit configuration outranks exact trusted provider cache, which outranks builtin context. The user-selected GPT-6 Luna 1,000,000-token context is retained even when the table publishes another limit. Published output limits are advisory; this feature does not replace explicit output budgets or introduce a new output default. It never changes profile/model ID, provider, endpoint, payer, protocol, credential or model selection.

The builtin resource has its own strict schema 1 and source provenance. Pricing includes nullable USD-per-million-token rates, context thresholds and published restrictions, shown as estimates rather than the configured gateway's actual charge. Existing `model-metadata.json` schema 1 remains capability-only and unchanged; prices or builtin fuzzy matches never enter it. Old/unsupported schemas are rejected without migration.

## States, events and ownership

- Catalog resource load: `unloaded -> ready` only after full validation; malformed/missing data gives `unavailable` and a redacted stable failure.
- Model-name edit or provider `/models` choice: `unmatched -> exact|normalized|similar` for an eligible best match, otherwise `unmatched`.
- Required-context resolve: `resolved_explicit`, then `resolved_trusted`, then `resolved_builtin`, else `context_required`.
- Context edit: automatic draft ownership becomes manual; the player's value wins. Changing model invalidates prior automatic provenance and resolves the new missing value.
- Save/reload: existing asynchronous preparation and atomic publication affect future requests only. Active requests keep their captured immutable runtime.
- Developer update: explicit public-source pull creates a proposal, fixture/strict validation and review precede committed resource replacement. Runtime performs no new network fetch or billable operation.

The release resource owns an immutable catalog generation; the loader owns context precedence; settings owns editable drafts/provenance; the existing registry owns runtime publication. The metadata cache remains the exact trusted-provider cache owner. There is no second runtime model registry.

## Failure and ambiguity semantics

A malformed/duplicate/unsupported builtin resource is rejected as a whole, not partially imported. Explicit and cached limits remain usable. Missing/unrelated model context fails as `invalid_model_config` until manually repaired. Network/cache refresh failure preserves existing cache/configuration; the bundled table remains offline. Equally close eligible candidates select a deterministic best and expose its source; ambiguous family/version candidates are ineligible rather than assigning an unrelated model. Data contains no credentials or raw provider errors.

## Supersedes

Supersedes only SKMB-009's requirement that external catalogs suggest values only when explicitly enabled: the user explicitly approved automatic builtin matching by default. SKMB-009 selection/captured-runtime rules, SKMB-012 trusted cache schema/precedence and SKMB-015 atomic settings/probe separation remain authoritative.

## Verification

See `docs/superpowers/plans/2026-10-01-builtin-model-catalog.md` and `docs/superpowers/specs/2026-10-01-builtin-model-catalog.md`. Deterministic validation covers exact/alias/suffix/fuzzy matching, family/version rejection, ties/unknowns, malformed schema/provenance, manual/trusted precedence, cache strictness, redaction and frozen request runtimes. Root controls Java25 build slots and the final both-loader gate.

Untouched automatic draft context remains `null`/omitted when saved. Save is not manual adoption. Reopening recomputes the effective context; later trusted metadata can replace builtin context. Only an actual context edit stores an explicit numeric value.

### Deterministic matching contract

Matching indexes all 1,121 entries without a row cap. Canonical and upstream IDs are exact identity candidates; explicit published aliases follow. Rank order is match class (exact, normalized, similar), similarity descending, identity rank (canonical ID, own upstream ID, alias), author/direct provider before the known hosted sources OpenRouter and Amazon Bedrock, then canonical ID lexicographic. Qualified canonical IDs therefore beat another provider's canonical alias. No ranking operation changes the configured request ID.

Normalization strips slash-delimited gateway/provider wrappers down to the final model segment. It folds case, whitespace/underscores/hyphens and dotted numeric version separators. The only stripped tails are `:free`, `:nitro`, `:floor`, `:online`, `-latest`, and a terminal full `20YYMMDD` / `20YY-MM-DD` date. Other tails, including preview, thinking, mini, nano, pro and custom gateway names, remain identity tokens. Similar matching requires the same exact recognizable family prefix, identical numeric version sequence and the same number of nonnumeric identity words. Different words require both lengths at least four and per-word similarity at least 0.75. Overall normalized Levenshtein similarity must be at least 0.82. This permits real close spellings such as `gpt-6-lunna` while rejecting missing/different variants, unrelated families and different versions. Ties use the order above, not resource order, and expose the chosen model/source.

Static candidate names, aliases, numbers and identity words are indexed once per immutable catalog. The most recent match is memoized. The screen caches its display projection by draft and effective resolution, so rendering does not repeatedly scan or normalize the table.
