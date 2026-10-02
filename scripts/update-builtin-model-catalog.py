#!/usr/bin/env python3
"""Opt-in public refresh for the offline built-in model reference catalog.

This development tool never reads local model configuration or credentials and
never calls inference endpoints. Network access requires --fetch-public. For
reproducible/offline refreshes, supply the three saved public JSON inputs and
--captured-at. Raw upstream catalogs are not copied into the repository.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_SOURCES = {
    "models-dev": ("Models.dev provider catalog", "https://models.dev/api.json"),
    "models-dev-canonical": ("Models.dev canonical capabilities", "https://models.dev/models.json"),
    "openrouter": ("OpenRouter public model catalog", "https://openrouter.ai/api/v1/models"),
}
# Selected provider APIs with useful published text-generation rates, including
# author APIs and Bedrock's distinct hosting IDs. Canonical capabilities and the
# complete OpenRouter list cover additional open-weight authors.
DIRECT_PROVIDERS = (
    "openai", "anthropic", "google", "deepseek", "alibaba", "xai",
    "mistral", "cohere", "llama", "meta", "moonshotai", "minimax", "zai",
    "zhipuai", "xiaomi", "stepfun", "inception", "ai21", "amazon-bedrock",
)
PRICE_FIELDS = ("input", "output", "cacheRead", "cacheWrite")
MODEL_FIELDS = {
    "id", "provider", "family", "aliases", "contextWindowTokens",
    "maxOutputTokens", "pricing", "capabilitySource", "pricingSource", "upstreamModelId",
    "imageInputCapability", "imageInputCapabilitySource",
}
SPECIALIZED = re.compile(
    r"(?:embedding|rerank|whisper|transcri|speech|(?:^|[-_/])tts(?:[-_/]|$)|"
    r"(?:^|[-_/])asr(?:[-_/]|$)|gpt-image|imagen|veo|lyria)", re.IGNORECASE
)


def instant(value: str) -> str:
    if not isinstance(value, str):
        raise ValueError("Timestamp must be ISO-8601 text")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Timestamp must include a timezone")
    return parsed.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def positive_integer(value):
    if value is None or isinstance(value, bool):
        return None
    try:
        number = Decimal(str(value))
    except InvalidOperation:
        return None
    if not number.is_finite() or number <= 0 or number != number.to_integral_value():
        return None
    return int(number)


def decimal_string(value, factor=1):
    """Unknown, nonfinite, or upstream -1 dynamic prices stay unknown, not free."""
    if value is None or isinstance(value, bool):
        return None
    try:
        number = Decimal(str(value)) * factor
    except (InvalidOperation, ValueError, TypeError):
        return None
    if not number.is_finite() or number < 0:
        return None
    result = format(number, "f")
    if "." in result:
        result = result.rstrip("0").rstrip(".")
    return result if result not in ("", "-0") else "0"


def generation_model(model: dict, *, router=False) -> bool:
    modalities = model.get("architecture" if router else "modalities") or {}
    inputs = modalities.get("input_modalities" if router else "input") or []
    outputs = modalities.get("output_modalities" if router else "output") or []
    # Legacy catalogs sometimes mark embedding models as text output. Reject
    # their explicit published identity instead of treating vector APIs as chat.
    identity = " ".join(str(model.get(key, "")) for key in ("id", "name", "family"))
    return "text" in inputs and "text" in outputs and not SPECIALIZED.search(identity)


def image_input_capability(model: dict, *, router=False) -> str:
    """Only an explicit published input-modality list establishes support."""
    descriptor = model.get("architecture" if router else "modalities")
    if descriptor is None:
        return "unknown"
    if not isinstance(descriptor, dict):
        raise ValueError("Malformed model modalities")
    inputs = descriptor.get("input_modalities" if router else "input")
    if inputs is None:
        return "unknown"
    if not isinstance(inputs, list) or any(not isinstance(item, str) or not item.strip() for item in inputs):
        raise ValueError("Input modalities must be an array of nonblank strings")
    return "supported" if "image" in inputs else "unsupported"


def family_name(model: dict, fallback="") -> str:
    identity = " ".join(str(model.get(key, "")) for key in ("family", "id", "name")).lower()
    if "gpt-oss" in identity:
        return "gpt-oss"
    prefixes = (
        ("claude", "claude"), ("gemini", "gemini"), ("gemma", "gemma"),
        ("deepseek", "deepseek"), ("qwen", "qwen"), ("qwq", "qwen"),
        ("qvq", "qwen"), ("llama", "llama"), ("grok", "grok"),
        ("minimax", "minimax"), ("kimi", "kimi"), ("glm", "glm"),
        ("gpt", "gpt"), ("nemotron", "nemotron"),
    )
    for prefix, family in prefixes:
        if prefix in identity:
            return family
    if re.search(r"(?:^|[/\s])o[134](?:[-\s]|$)", identity) or str(model.get("family", "")).startswith("o-"):
        return "o"
    raw_family = str(model.get("family") or fallback).lower()
    if raw_family.startswith(("mistral", "mixtral", "ministral", "pixtral")):
        return "mistral"
    if not raw_family:
        raw_family = str(model.get("id", "model")).split("/")[-1].split("-")[0]
    return re.sub(r"[^a-z0-9]+", "-", raw_family).strip("-") or "model"


def aliases(*values) -> list[str]:
    unique = {}
    for value in sorted({value.strip() for value in values
                         if isinstance(value, str) and value.strip()}):
        unique.setdefault(value.casefold(), value)
    return sorted(unique.values())


def tier_from(values: dict, threshold: int, *, router=False) -> dict:
    source_fields = (
        ("prompt", "completion", "input_cache_read", "input_cache_write")
        if router else ("input", "output", "cache_read", "cache_write")
    )
    factor = 1_000_000 if router else 1
    return {"minInputTokens": threshold, **{
        target: decimal_string(values.get(source), factor)
        for target, source in zip(PRICE_FIELDS, source_fields)
    }}


def rate_condition_note(label: str, values: dict, *, router=False) -> str:
    rates = tier_from(values, 0, router=router)
    summary = ", ".join(field + "=" + rates[field]
                        for field in PRICE_FIELDS if rates[field] is not None)
    return label + " (USD per million tokens): " + summary + "." if summary else ""


def pricing(model: dict, *, router=False, provider=""):
    cost = model.get("pricing" if router else "cost")
    if not isinstance(cost, dict) or not cost:
        return None
    tiers = [tier_from(cost, 0, router=router)]
    notes = []
    variants = cost.get("overrides" if router else "tiers") or []
    for variant in variants:
        if not isinstance(variant, dict):
            raise ValueError("Malformed price variant")
        if router:
            if "min_prompt_tokens" not in variant:
                # Time-based overrides cannot be represented as context tiers.
                # Preserve their published conditions and normalized rates in
                # the note, rather than silently applying them to every prompt.
                conditions = {key: value for key, value in variant.items()
                              if key not in {"prompt", "completion", "input_cache_read", "input_cache_write"}}
                note = rate_condition_note("Rate condition " + json.dumps(conditions, sort_keys=True),
                                           variant, router=True)
                if note:
                    notes.append(note)
                continue
            threshold = positive_integer(variant.get("min_prompt_tokens"))
        else:
            descriptor = variant.get("tier") or {}
            if descriptor.get("type") != "context":
                note = rate_condition_note("Rate condition " + json.dumps(descriptor, sort_keys=True), variant)
                if note:
                    notes.append(note)
                continue
            threshold = positive_integer(descriptor.get("size"))
        if threshold is None:
            raise ValueError("Published context price tier has no positive token threshold")
        tiers.append(tier_from(variant, threshold, router=router))
    legacy_context = cost.get("context_over_200k")
    if not variants and isinstance(legacy_context, dict):
        tiers.append(tier_from(legacy_context, 200_000, router=router))
    if len({tier["minInputTokens"] for tier in tiers}) != len(tiers):
        raise ValueError("Duplicate context price threshold")
    tiers.sort(key=lambda item: item["minInputTokens"])
    # The captured official DeepSeek pricing page names these Flash aliases and
    # their off-peak rates. Do not project that schedule onto another model or a
    # later changed base rate. The current Pro catalog rates differ from that
    # page, so no unsupported Pro schedule is asserted here.
    flash_ids = {"deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp"}
    if (provider == "deepseek" and model.get("id") in flash_ids
            and [tiers[0][field] for field in ("input", "output", "cacheRead")] == ["0.15", "0.6", "0.003"]):
        notes.append(
            "Base rates are off-peak. Peak: Monday-Friday 01:00-04:00 and 06:00-10:00 UTC, "
            "except Chinese public holidays; USD per million tokens input=0.3, output=1.2, cacheRead=0.006."
        )
    experimental = model.get("experimental") or {}
    modes = experimental.get("modes") if isinstance(experimental, dict) else None
    for name, mode in sorted((modes or {}).items()):
        mode_cost = mode.get("cost") if isinstance(mode, dict) else None
        if isinstance(mode_cost, dict):
            note = rate_condition_note(str(name) + " mode", mode_cost)
            if note:
                notes.append(note)
    return {"currency": "USD", "unit": "million_tokens", "tiers": tiers, "note": " ".join(notes)}


def catalog_entry(provider: str, upstream: str, model: dict, source: str, *, router=False) -> dict:
    if not isinstance(upstream, str) or not upstream.strip():
        raise ValueError("Model ID must be nonblank text")
    context = positive_integer(model.get("context_length") if router else (model.get("limit") or {}).get("context"))
    if context is None:
        raise ValueError("Model context is not published")
    output = ((model.get("top_provider") or {}).get("max_completion_tokens")
              if router else (model.get("limit") or {}).get("output"))
    published_aliases = [upstream, model.get("name"), model.get("canonical_model_id")]
    if router:
        published_aliases += [model.get("canonical_slug"), model.get("hugging_face_id")]
        target = model.get("alias_target") or {}
        # Target identity is an explicit published alias, not a guessed date/version.
        published_aliases.append(target.get("slug"))
    rates = pricing(model, router=router, provider=provider)
    return {
        "id": provider + "/" + upstream,
        "provider": provider,
        "family": family_name(model, fallback=upstream.split("/")[0] if router else provider),
        "aliases": aliases(*published_aliases),
        "contextWindowTokens": context,
        "maxOutputTokens": positive_integer(output),
        "pricing": rates,
        "capabilitySource": source,
        "pricingSource": source if rates is not None else None,
        "upstreamModelId": upstream,
        "imageInputCapability": image_input_capability(model, router=router),
        "imageInputCapabilitySource": source,
    }


def build_catalog(provider_catalog: dict, canonical_catalog: dict, router_catalog: dict,
                  captured_at, catalog_version=None, published_at=None) -> dict:
    """Pure deterministic normalizer. Inputs are public JSON, not local configs."""
    captures = ({key: instant(captured_at[key]) for key in PUBLIC_SOURCES}
                if isinstance(captured_at, dict) else
                {key: instant(captured_at) for key in PUBLIC_SOURCES})
    snapshot_time = max(captures.values())
    published_at = instant(published_at or snapshot_time)
    if not all(isinstance(value, dict) for value in (provider_catalog, canonical_catalog, router_catalog)):
        raise ValueError("Public source roots must be JSON objects")
    entries = {}
    for provider in DIRECT_PROVIDERS:
        published = provider_catalog.get(provider)
        if published is None:
            continue
        if not isinstance(published, dict) or not isinstance(published.get("models"), dict):
            raise ValueError("Malformed provider model map: " + provider)
        for upstream, model in sorted(published["models"].items()):
            if not isinstance(model, dict):
                raise ValueError("Malformed provider model")
            if not generation_model(model) or positive_integer((model.get("limit") or {}).get("context")) is None:
                continue
            entries[provider + "/" + upstream] = catalog_entry(provider, upstream, model, "models-dev")
    for canonical_id, model in sorted(canonical_catalog.items()):
        if not isinstance(model, dict) or "/" not in canonical_id:
            raise ValueError("Malformed canonical model")
        if canonical_id in entries or not generation_model(model) or positive_integer((model.get("limit") or {}).get("context")) is None:
            continue
        provider, upstream = canonical_id.split("/", 1)
        # Canonical author metadata intentionally has no hosting price. Even if
        # future source fields add aggregate rates, do not imply an author charge.
        capability = dict(model)
        capability.pop("cost", None)
        capability["canonical_model_id"] = canonical_id
        entries[canonical_id] = catalog_entry(provider, upstream, capability, "models-dev-canonical")
    rows = router_catalog.get("data")
    if not isinstance(rows, list):
        raise ValueError("OpenRouter response data must be an array")
    published_count = router_catalog.get("total_count")
    if published_count is not None and positive_integer(published_count) != len(rows):
        raise ValueError("OpenRouter returned a partial list; do not silently cap the catalog")
    links = router_catalog.get("links") or {}
    if links.get("next"):
        raise ValueError("OpenRouter returned pagination; full-list fetch must be reviewed")
    seen_router = set()
    for model in rows:
        if not isinstance(model, dict) or not isinstance(model.get("id"), str):
            raise ValueError("Malformed OpenRouter model")
        upstream = model["id"]
        if upstream in seen_router:
            raise ValueError("Duplicate OpenRouter model ID: " + upstream)
        seen_router.add(upstream)
        if not generation_model(model, router=True) or positive_integer(model.get("context_length")) is None:
            continue
        entry = catalog_entry("openrouter", upstream, model, "openrouter", router=True)
        entries[entry["id"]] = entry
    result = {
        "catalogVersion": catalog_version or snapshot_time[:10],
        "publishedAt": published_at,
        "sources": [
            {"id": key, "label": label, "url": url, "capturedAt": captures[key]}
            for key, (label, url) in PUBLIC_SOURCES.items()
        ],
        "models": [entries[key] for key in sorted(entries)],
    }
    validate_catalog(result)
    return result


def validate_catalog(catalog: dict) -> None:
    if set(catalog) != {"catalogVersion", "publishedAt", "sources", "models"}:
        raise ValueError("Catalog root schema mismatch")
    if not isinstance(catalog["catalogVersion"], str) or not catalog["catalogVersion"].strip():
        raise ValueError("Catalog version must be nonblank text")
    instant(catalog["publishedAt"])
    source_ids = set()
    for source in catalog["sources"]:
        if set(source) != {"id", "label", "url", "capturedAt"}:
            raise ValueError("Source schema mismatch")
        if any(not isinstance(source[field], str) or not source[field].strip() for field in source):
            raise ValueError("Source values must be nonblank text")
        url = urlsplit(source["url"])
        if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError("Source URL must be public HTTPS without credentials, query or fragment")
        instant(source["capturedAt"])
        if source["id"] in source_ids:
            raise ValueError("Duplicate source ID")
        source_ids.add(source["id"])
    ids = set()
    for model in catalog["models"]:
        if set(model) != MODEL_FIELDS:
            raise ValueError("Model schema mismatch")
        for field in ("id", "provider", "family", "upstreamModelId"):
            if not isinstance(model[field], str) or not model[field].strip():
                raise ValueError("Model identity must be nonblank text")
        if model["id"] != model["provider"] + "/" + model["upstreamModelId"] or model["id"] in ids:
            raise ValueError("Model identity mismatch or duplicate")
        ids.add(model["id"])
        if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", model["family"]):
            raise ValueError("Model family must be lowercase")
        if not isinstance(model["aliases"], list) or model["aliases"] != aliases(*model["aliases"]):
            raise ValueError("Aliases must be nonblank, sorted and unique")
        if type(model["contextWindowTokens"]) is not int or model["contextWindowTokens"] <= 0:
            raise ValueError("Context must be a positive integer")
        output = model["maxOutputTokens"]
        if output is not None and (type(output) is not int or output <= 0):
            raise ValueError("Max output must be positive or null")
        if model["capabilitySource"] not in source_ids:
            raise ValueError("Missing capability source")
        if model["imageInputCapability"] not in {"supported", "unsupported", "unknown"}:
            raise ValueError("Image input capability must be supported, unsupported or unknown")
        image_source = model["imageInputCapabilitySource"]
        if ((image_source is not None and image_source not in source_ids)
                or (model["imageInputCapability"] != "unknown" and image_source is None)):
            raise ValueError("Known image input capability requires a published source")
        price = model["pricing"]
        if price is None:
            if model["pricingSource"] is not None:
                raise ValueError("Unknown pricing must not claim a pricing source")
            continue
        if model["pricingSource"] not in source_ids:
            raise ValueError("Missing pricing source")
        if set(price) != {"currency", "unit", "tiers", "note"} or price["currency"] != "USD" or price["unit"] != "million_tokens" or not isinstance(price["note"], str):
            raise ValueError("Pricing schema mismatch")
        if not isinstance(price["tiers"], list) or not price["tiers"]:
            raise ValueError("At least a base price tier is required")
        previous = -1
        for index, tier in enumerate(price["tiers"]):
            if set(tier) != {"minInputTokens", *PRICE_FIELDS}:
                raise ValueError("Price tier schema mismatch")
            threshold = tier["minInputTokens"]
            if type(threshold) is not int or threshold <= previous or (index == 0 and threshold != 0):
                raise ValueError("Price tiers must start at zero and strictly increase")
            previous = threshold
            for field in PRICE_FIELDS:
                value = tier[field]
                if value is not None and (not isinstance(value, str) or not re.fullmatch(r"(?:0|[1-9][0-9]*)(?:\.[0-9]+)?", value) or decimal_string(value) is None):
                    raise ValueError("Prices must be nonnegative decimal strings or null")


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        raise ValueError("Public catalog redirect refused; review the allowlist before refreshing")


def fetch_public(source: str) -> dict:
    if source not in PUBLIC_SOURCES:
        raise ValueError("Source is not allowlisted")
    url = PUBLIC_SOURCES[source][1]
    request = Request(url, headers={"Accept": "application/json", "User-Agent": "OpenAllay-public-model-catalog-refresh/1"})
    with build_opener(NoRedirects()).open(request, timeout=45) as response:
        if response.status != 200:
            raise ValueError("Public catalog HTTP request failed")
        return json.loads(response.read().decode("utf-8"), parse_float=Decimal)


def fetch_snapshot(source: str):
    data = fetch_public(source)
    return data, instant(datetime.now(timezone.utc).isoformat())


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"), parse_float=Decimal)


def write_catalog(path: Path, catalog: dict) -> None:
    validate_catalog(catalog)
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
    temporary = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent,
                                         prefix=path.name + ".", suffix=".tmp", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(encoded)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fetch-public", action="store_true", help="Opt in to the three fixed public catalog GET requests; no auth")
    parser.add_argument("--models-dev", type=Path, help="Saved public models.dev/api.json input")
    parser.add_argument("--models-dev-canonical", type=Path, help="Saved public models.dev/models.json input")
    parser.add_argument("--openrouter", type=Path, help="Saved public OpenRouter/api/v1/models input")
    parser.add_argument("--captured-at", help="Default ISO timestamp for saved inputs; required in offline mode")
    for source in PUBLIC_SOURCES:
        parser.add_argument("--" + source + "-captured-at", help="Per-source saved-input ISO timestamp override")
    parser.add_argument("--published-at", help="Snapshot publication ISO timestamp; defaults to captured-at")
    parser.add_argument("--catalog-version", help="Snapshot version; defaults to capture UTC date")
    parser.add_argument("--output", type=Path, required=True, help="Explicit destination (review output before copying to bundled resource)")
    args = parser.parse_args(argv)
    local_inputs = (args.models_dev, args.models_dev_canonical, args.openrouter)
    capture_overrides = {source: getattr(args, source.replace("-", "_") + "_captured_at")
                         for source in PUBLIC_SOURCES}
    if args.fetch_public and (any(local_inputs) or args.captured_at or any(capture_overrides.values())):
        parser.error("--fetch-public cannot be combined with local inputs or overridden capture timestamps")
    if not args.fetch_public and (not all(local_inputs) or not args.captured_at):
        parser.error("Offline mode requires all three local inputs and --captured-at; network is opt-in only")
    try:
        if args.fetch_public:
            with ThreadPoolExecutor(max_workers=len(PUBLIC_SOURCES)) as worker:
                snapshots = dict(zip(PUBLIC_SOURCES, worker.map(fetch_snapshot, PUBLIC_SOURCES)))
            source_data = {key: value[0] for key, value in snapshots.items()}
            captured_at = {key: value[1] for key, value in snapshots.items()}
        else:
            source_data = dict(zip(PUBLIC_SOURCES, map(load_json, local_inputs)))
            captured_at = {key: instant(value or args.captured_at)
                           for key, value in capture_overrides.items()}
        catalog = build_catalog(*source_data.values(), captured_at, args.catalog_version, args.published_at)
        write_catalog(args.output, catalog)
        print(f"Wrote {len(catalog['models'])} model references to {args.output}; no result cap. Review upstream terms before publishing.")
        return 0
    except (OSError, ValueError, TypeError, KeyError) as error:
        print("Catalog refresh failed: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
