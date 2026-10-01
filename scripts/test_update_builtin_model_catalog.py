"""Offline fixture tests for the public model catalog refresh. No provider calls."""
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = spec_from_file_location("update_builtin_model_catalog", ROOT / "scripts/update-builtin-model-catalog.py")
update = module_from_spec(SPEC)
SPEC.loader.exec_module(update)
CAPTURE = "2026-09-30T16:22:41Z"


def author(model_id="gpt-6-luna", **changes):
    row = {
        "id": model_id, "name": "GPT-6 Luna", "family": "gpt-luna",
        "modalities": {"input": ["text", "image"], "output": ["text"]},
        "limit": {"context": 1050000, "output": 128000},
        "cost": {"input": 0.1, "output": 0.5, "cache_read": 0.01,
                 "tiers": [{"input": 0.2, "output": 0.75, "cache_read": 0.02,
                            "tier": {"type": "context", "size": 272000}}]},
    }
    row.update(changes)
    return row


def router(model_id="openai/gpt-6-luna", **changes):
    row = {
        "id": model_id, "name": "OpenAI: GPT-6 Luna", "canonical_slug": "openai/gpt-6-luna-20260922",
        "architecture": {"input_modalities": ["text", "image"], "output_modalities": ["text"]},
        "context_length": 1050000, "top_provider": {"max_completion_tokens": 128000},
        "pricing": {"prompt": "0.0000001", "completion": "0.0000005", "input_cache_read": "0.00000001",
                    "overrides": [{"min_prompt_tokens": 272000, "prompt": "0.0000002", "completion": "0.00000075"}]},
    }
    row.update(changes)
    return row


def inputs():
    return [{"openai": {"models": {"gpt-6-luna": author()}}}, {}, {"data": [router()], "total_count": 1, "links": {"next": None}}]


def build(data=None):
    return update.build_catalog(*(data or inputs()), CAPTURE)


class PublicCatalogTest(unittest.TestCase):
    def test_catalog_root_keeps_only_payload_and_snapshot_provenance(self):
        result = build()
        self.assertEqual({"catalogVersion", "publishedAt", "sources", "models"}, set(result))
        self.assertEqual("2026-09-30", result["catalogVersion"])
        for invalid in ({**result, "extra": True},
                        {key: value for key, value in result.items() if key != "sources"}):
            with self.assertRaisesRegex(ValueError, "root schema mismatch"):
                update.validate_catalog(invalid)

    def test_provider_prices_not_collapsed_and_decimal_units(self):
        result = build()
        direct, gateway = result["models"]
        self.assertEqual("openai/gpt-6-luna", direct["id"])
        self.assertEqual("openrouter/openai/gpt-6-luna", gateway["id"])
        self.assertEqual("openai/gpt-6-luna", gateway["upstreamModelId"])
        for row in result["models"]:
            self.assertEqual("gpt", row["family"])
            self.assertEqual(1050000, row["contextWindowTokens"])
            self.assertEqual(128000, row["maxOutputTokens"])
            self.assertEqual("USD", row["pricing"]["currency"])
            self.assertEqual("million_tokens", row["pricing"]["unit"])
            self.assertEqual([0, 272000], [tier["minInputTokens"] for tier in row["pricing"]["tiers"]])
            self.assertEqual("0.1", row["pricing"]["tiers"][0]["input"])
            self.assertEqual("0.5", row["pricing"]["tiers"][0]["output"])
            self.assertEqual("0.01", row["pricing"]["tiers"][0]["cacheRead"])
            self.assertEqual("0.2", row["pricing"]["tiers"][1]["input"])
            self.assertEqual("", row["pricing"]["note"])
        self.assertEqual("models-dev", direct["pricingSource"])
        self.assertEqual("openrouter", gateway["pricingSource"])
        self.assertIsNone(gateway["pricing"]["tiers"][1]["cacheRead"])

    def test_unknowns_remain_null_not_free(self):
        sources = inputs()
        sources[0]["openai"]["models"]["gpt-6-luna"].pop("cost")
        sources[0]["openai"]["models"]["gpt-6-luna"]["limit"]["output"] = None
        sources[2]["data"][0]["pricing"] = {"prompt": "-1", "completion": "-1"}
        direct, gateway = build(sources)["models"]
        self.assertIsNone(direct["pricing"])
        self.assertIsNone(direct["pricingSource"])
        self.assertIsNone(direct["maxOutputTokens"])
        self.assertTrue(all(value is None for key, value in gateway["pricing"]["tiers"][0].items() if key != "minInputTokens"))
        self.assertEqual("", gateway["pricing"]["note"])
        self.assertEqual("0", update.decimal_string("0"))
        for value in ("-1", "NaN", "Infinity", "x", None, True):
            self.assertIsNone(update.decimal_string(value))

    def test_canonical_model_has_no_invented_hosting_price(self):
        sources = inputs()
        sources[1]["meta/llama-3.3-70b-instruct"] = author(
            "meta/llama-3.3-70b-instruct", name="Llama 3.3 70B", family="llama",
            limit={"context": 128000, "output": None})
        llama = next(row for row in build(sources)["models"] if row["provider"] == "meta")
        self.assertIsNone(llama["pricing"])
        self.assertIsNone(llama["pricingSource"])
        self.assertIsNone(llama["maxOutputTokens"])
        self.assertEqual("models-dev-canonical", llama["capabilitySource"])
        self.assertIn("meta/llama-3.3-70b-instruct", llama["aliases"])

    def test_direct_provider_wins_only_same_identity_canonical(self):
        sources = inputs()
        sources[1]["openai/gpt-6-luna"] = author(limit={"context": 1000000, "output": 64000})
        direct = next(row for row in build(sources)["models"] if row["provider"] == "openai")
        self.assertEqual(1050000, direct["contextWindowTokens"])
        self.assertEqual("models-dev", direct["capabilitySource"])
        self.assertEqual(2, len(build(sources)["models"]))

    def test_specialized_and_missing_context_excluded_not_capped(self):
        sources = inputs()
        models = sources[0]["openai"]["models"]
        models["text-embedding-3-small"] = author("text-embedding-3-small", name="Embedding", family="text-embedding")
        models["gpt-image-2"] = author("gpt-image-2", name="GPT Image", family="gpt-image")
        models["gpt-context-unknown"] = author("gpt-context-unknown", limit={"context": None})
        models["gpt-context-zero"] = author("gpt-context-zero", limit={"context": 0})
        rows = [router("test/model-" + str(index)) for index in range(1105)]
        sources[2] = {"data": rows, "total_count": len(rows)}
        result = build(sources)
        self.assertEqual(1106, len(result["models"]))
        self.assertFalse(any("embedding" in row["id"] or "image-2" in row["id"] for row in result["models"]))

    def test_partial_pagination_and_duplicate_router_fail(self):
        for patch_value in ({"total_count": 10}, {"links": {"next": "https://openrouter.ai/api/v1/models?offset=1"}}):
            sources = inputs()
            sources[2].update(patch_value)
            with self.assertRaisesRegex(ValueError, "partial|pagination"):
                build(sources)
        sources = inputs()
        sources[2] = {"data": [router(), router()]}
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            build(sources)

    def test_stable_sort_and_source_timestamps(self):
        sources = inputs()
        models = sources[0]["openai"]["models"]
        models["gpt-4.1-mini"] = author("gpt-4.1-mini", name="GPT-4.1 Mini", family="gpt-mini")
        first = build(sources)
        sources[0]["openai"]["models"] = dict(reversed(list(models.items())))
        self.assertEqual(first, build(sources))
        self.assertEqual(CAPTURE, first["publishedAt"])
        self.assertTrue(all(source["capturedAt"] == CAPTURE for source in first["sources"]))
        self.assertEqual(sorted(row["id"] for row in first["models"]), [row["id"] for row in first["models"]])
        self.assertEqual("2026-09-30T14:22:41Z", update.instant("2026-09-30T16:22:41+02:00"))
        with self.assertRaises(ValueError):
            update.instant("2026-09-30T16:22:41")

    def test_public_aliases_and_family_preserve_variants(self):
        sources = inputs()
        row = sources[2]["data"][0]
        row.update(id="openai/gpt-oss-120b:free", name="OpenAI: gpt-oss-120b (free)",
                   hugging_face_id="openai/gpt-oss-120b", canonical_slug="openai/gpt-oss-120b")
        normalized = next(model for model in build(sources)["models"] if model["provider"] == "openrouter")
        self.assertEqual("gpt-oss", normalized["family"])
        self.assertIn("openai/gpt-oss-120b:free", normalized["aliases"])
        self.assertIn("openai/gpt-oss-120b", normalized["aliases"])
        self.assertEqual("o", update.family_name(author("o3-mini", name="o3 Mini", family="o-mini")))
        self.assertEqual("claude", update.family_name(author("claude-sonnet-4-5", family="claude-sonnet")))
        self.assertEqual(["MODEL"], update.aliases("model", "MODEL", " Model "))
        self.assertEqual("model-1-5", update.family_name({"id": "sample", "family": "model.1.5"}))

    def test_price_tier_schema_and_invalid_variants_fail(self):
        sources = inputs()
        sources[0]["openai"]["models"]["gpt-6-luna"]["cost"]["tiers"][0]["tier"]["size"] = 0
        with self.assertRaisesRegex(ValueError, "threshold"):
            build(sources)
        for change in (lambda row: row["pricing"]["tiers"][0].update(input=0.1),
                       lambda row: row["pricing"]["tiers"][1].update(minInputTokens=0),
                       lambda row: row.update(maxOutputTokens=0)):
            result = build()
            change(result["models"][0])
            with self.assertRaises(ValueError):
                update.validate_catalog(result)

    def test_multi_threshold_and_non_context_notes(self):
        row = author(cost={"input": "1", "output": "2", "tiers": [
            {"input": "5", "output": "6", "tier": {"type": "context", "size": 256000}},
            {"input": "3", "output": "4", "tier": {"type": "context", "size": 32000}},
        ]})
        self.assertEqual([0, 32000, 256000], [tier["minInputTokens"] for tier in update.pricing(row)["tiers"]])
        self.assertEqual("", update.pricing(row, provider="deepseek")["note"])
        row["cost"]["tiers"] = [{"input": "10", "tier": {"type": "time", "size": 1}}]
        self.assertEqual(1, len(update.pricing(row)["tiers"]))
        self.assertIn('Rate condition {"size": 1, "type": "time"}', update.pricing(row)["note"])
        self.assertIn("input=10", update.pricing(row)["note"])
        row = router(pricing={"prompt": "0.00000066", "completion": "0.00000198", "overrides": [
            {"utc_days": ["monday"], "utc_start": 100, "utc_end": 400,
             "prompt": "0.00000132", "completion": "0.00000396"},
        ]})
        result = update.pricing(row, router=True)
        self.assertEqual(1, len(result["tiers"]))
        self.assertIn('"utc_start": 100', result["note"])
        self.assertIn("input=1.32", result["note"])
        self.assertIn("output=3.96", result["note"])

    def test_notes_only_preserve_specific_published_rate_conditions(self):
        simple = author(cost={"input": "1", "output": "2", "input_audio": "32"})
        self.assertEqual("", update.pricing(simple)["note"])
        simple["experimental"] = {"modes": {"pro": {"provider": {"body": {"reasoning": "pro"}}}}}
        self.assertEqual("", update.pricing(simple)["note"])
        simple["experimental"]["modes"]["fast"] = {"cost": {"input": "2", "output": "4", "cache_read": "0.2"}}
        self.assertEqual("fast mode (USD per million tokens): input=2, output=4, cacheRead=0.2.",
                         update.pricing(simple)["note"])
        flash = author("deepseek-flash", cost={"input": "0.15", "output": "0.6", "cache_read": "0.003"})
        note = update.pricing(flash, provider="deepseek")["note"]
        self.assertIn("Base rates are off-peak", note)
        self.assertIn("01:00-04:00 and 06:00-10:00 UTC", note)
        self.assertIn("input=0.3, output=1.2, cacheRead=0.006", note)
        flash["cost"]["input"] = "0.2"
        self.assertEqual("", update.pricing(flash, provider="deepseek")["note"])
        flash["id"] = "deepseek-future"
        flash["cost"]["input"] = "0.15"
        self.assertEqual("", update.pricing(flash, provider="deepseek")["note"])

    def test_source_specific_capture_times(self):
        captures = {source: CAPTURE for source in update.PUBLIC_SOURCES}
        captures["models-dev-canonical"] = "2026-09-30T16:26:15Z"
        result = update.build_catalog(*inputs(), captures)
        self.assertEqual("2026-09-30T16:26:15Z", result["publishedAt"])
        self.assertEqual(captures, {source["id"]: source["capturedAt"] for source in result["sources"]})

    def test_network_is_allowlisted_and_redirects_refused(self):
        with patch.object(update, "build_opener") as opener:
            with self.assertRaisesRegex(ValueError, "allowlisted"):
                update.fetch_public("https://private.example/models")
            opener.assert_not_called()
        with self.assertRaisesRegex(ValueError, "redirect"):
            update.NoRedirects().redirect_request(None, None, 302, "", {}, "https://other.example")
        result = build()
        result["sources"][0]["url"] += "?api_key=not-allowed"
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            update.validate_catalog(result)

    def test_failed_refresh_preserves_existing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "proposal.json"
            destination.write_text("existing reviewed catalog", encoding="utf-8")
            with patch.object(update, "fetch_snapshot", side_effect=ValueError("source unavailable")):
                self.assertEqual(1, update.main(["--fetch-public", "--output", str(destination)]))
            self.assertEqual("existing reviewed catalog", destination.read_text())

    def test_cli_offline_requires_all_inputs_and_no_default_network(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "proposal.json"
            with patch.object(update, "fetch_public") as network:
                with self.assertRaises(SystemExit):
                    update.main(["--output", str(destination)])
                network.assert_not_called()
                paths = []
                for index, data in enumerate(inputs()):
                    path = Path(directory) / (str(index) + ".json")
                    path.write_text(json.dumps(data), encoding="utf-8")
                    paths.append(path)
                code = update.main([
                    "--models-dev", str(paths[0]), "--models-dev-canonical", str(paths[1]),
                    "--openrouter", str(paths[2]), "--captured-at", CAPTURE, "--output", str(destination),
                ])
                self.assertEqual(0, code)
                self.assertEqual(build(), json.loads(destination.read_text()))
                network.assert_not_called()


if __name__ == "__main__":
    unittest.main()
