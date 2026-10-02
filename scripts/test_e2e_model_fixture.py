import importlib.util
import json
import shutil
import subprocess
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name("e2e-model-fixture.py")
spec = importlib.util.spec_from_file_location("e2e_model_fixture", MODULE_PATH)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


# Exact model Tool results from the disposable Fabric run
# manual-regressions-fabric-20261003-033005. No original profile was accessed.
ACTUAL_MISSING_NATIVE_RECIPE = 'status: failure\ncode: javascript_error\nmessage: Error: Current native recipe is unavailable\nat openallay-agent.js:1'
ACTUAL_NULL_RECIPE_COMPLETE_VIEW = 'result: r_8ec20e8a3a_1 (current request only)\ntype: object\ncardinality: 4\nsize: 65 UTF-8 byte(s)\nscope: complete\nschema:\ningredients: array[0]\nsources: array[0]\n$: object{4} fields[recipe,craftability,ingredients,sources]\npreview:\nsources:\n  (empty)\nrecipe: null\ningredients:\n  (empty)\ncraftability: null\nnext: answer from this complete result; do not call run_javascript again only to verify it\ninput completeness: COMPLETE, PARTIAL; mixed authority: true\ninput coverage: CLIENT_VISIBLE COMPLETE; CLIENT_VISIBLE PARTIAL; RESOURCE_ASSET COMPLETE'


def labelled_projection(preview, result_type="object", complete=True, cardinality=1, size=100):
    """Synthetic unit-only current receipt; not evidence of native acceptance."""
    scope = "complete" if complete else "preview"
    ending = ("next: answer from this complete result; do not call run_javascript again only to verify it"
              if complete else "omitted: 8 row(s), 0 field(s) or nested value(s); "
              'use handles: ["r_test_1"] with workspace.open("r_test_1") in this request')
    return (f"result: r_test_1 (current request only)\ntype: {result_type}\n"
            f"cardinality: {cardinality}\nsize: {size} UTF-8 byte(s)\nscope: {scope}\n"
            "schema:\n$: " + result_type + "\npreview:\n" + preview + "\n" + ending)


def captured_recipe(generation="e" * 64, recipe_id="openallay:client_recipe_display/17"):
    return {"id": recipe_id,
            "reference": {"sourceId": "minecraft:client_recipe_book", "generation": generation,
                          "recipeId": recipe_id},
            "outputs": [{"stack": {"itemId": fixture.RECIPE_OUTPUT, "count": 1}}]}


def render_labelled(value, indent=0, list_prefix=None):
    """Mirror current JsonResultProjection.render formatting for unit inputs."""
    padding = " " * indent
    if not isinstance(value, (list, dict)):
        return padding + (list_prefix or "") + json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    if isinstance(value, list):
        if not value:
            return padding + (list_prefix or "") + "(empty)"
        return "\n".join(render_labelled(row, indent, "- ") for row in value)
    if not value:
        return padding + (list_prefix or "") + "(empty object)"
    output = padding + list_prefix if list_prefix is not None else ""
    for index, (key, child) in enumerate(value.items()):
        if index:
            output += "\n"
        output += (padding if index == 0 and list_prefix is None else
                   " " * (indent + (2 if list_prefix is not None else 0))) + key + ":"
        if isinstance(child, (list, dict)):
            output += "\n" + render_labelled(child, indent + (4 if list_prefix is not None else 2))
        else:
            output += " " + json.dumps(child, ensure_ascii=False, separators=(",", ":"))
    return output


def tool_message(value):
    result_type = ("null" if value is None else "boolean" if isinstance(value, bool)
                   else "number" if isinstance(value, (int, float))
                   else "object" if isinstance(value, dict)
                   else "array" if isinstance(value, list) else "string")
    cardinality = len(value) if isinstance(value, (dict, list)) else 0 if value is None else 1
    size = len(json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode())
    return {"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
            "content": labelled_projection(render_labelled(value), result_type,
                                           cardinality=cardinality, size=size)}


class JavascriptFixtureTests(unittest.TestCase):
    def test_default_request_uses_current_javascript_tool_only(self):
        arguments = fixture.javascript_arguments()
        self.assertNotIn("roots", arguments)
        self.assertIn("mc.recipes", arguments["source"])
        self.assertIn('require("openallay:crafting").allocate', arguments["source"])
        self.assertNotIn("search_recipes", arguments["source"])
        self.assertEqual("openallay__run_javascript", fixture.JAVASCRIPT_TOOL)

    def test_result_requires_current_tool_success_and_recipe_reference(self):
        recipe = {"id": "openallay:client_recipe_display/17",
                  "reference": {"sourceId": "minecraft:client_recipe_book",
                                "generation": "a" * 64,
                                "recipeId": "openallay:client_recipe_display/17"},
                  "outputs": [{"stack": {"itemId": fixture.RECIPE_OUTPUT, "count": 1}}]}
        request = {"messages": [tool_message(json.dumps({
            "recipe": recipe, "craftability": None, "ingredients": [], "sources": []}))]}
        self.assertEqual(recipe["reference"], fixture.recipe_reference(request))
        self.assertEqual(recipe, fixture.javascript_result(request)["recipe"])

    def test_fails_closed_when_tool_result_has_no_captured_recipe(self):
        request = {"messages": [{"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                                  "content": "status: failure\ncode: javascript_error\nmessage: missing recipe"}]}
        with self.assertRaises(ValueError):
            fixture.assistant_content(request, 1)

    def test_component_output_uses_the_exact_captured_reference_and_values(self):
        reference = {"sourceId": "minecraft:client_recipe_book",
                     "generation": "b" * 64, "recipeId": "openallay:client_recipe_display/17"}
        preview = {"recipe": {"id": reference["recipeId"], "reference": reference,
                              "outputs": [{"stack": {"itemId": fixture.RECIPE_OUTPUT, "count": 1}}]},
                   "craftability": {"craftable": True, "conclusive": True,
                                    "requestedCrafts": 1, "maximumCrafts": 2},
                   "ingredients": [{"itemId": "minecraft:iron_ingot", "required": 1,
                                    "available": 3}],
                   "sources": [{"sourceId": "patchouli:resources"}]}
        request = {"messages": [tool_message(json.dumps(preview))]}
        content = fixture.assistant_content(request, 1)
        components = [json.loads(block.split("```", 1)[0].strip())
                      for block in content.split("```openallay-component")[1:]]
        self.assertEqual(4, len(components))
        for component in components:
            self.assertEqual({"type", "properties", "fallback", "narration"}, set(component))
        self.assertIn(reference["generation"], content)
        self.assertIn('"craftable":true', content)
        self.assertIn('"available":3', content)
        self.assertIn("固定验收文本，不代表真实模型生成", content)


class ManualGraphicalRegressionFixtureTests(unittest.TestCase):
    def test_first_tool_returns_the_closed_current_recipe_not_an_imitation(self):
        arguments = fixture.manual_recipe_arguments()
        self.assertIn("mc.recipes[recipeIndex]", arguments["source"])
        self.assertIn("return recipe", arguments["source"])
        self.assertNotIn("return {", arguments["source"])
        self.assertNotIn("Java", arguments["source"])
        self.assertNotIn("commands", arguments["source"])
        self.assertIn("原生配方", arguments["title"])

    def test_long_chinese_content_keeps_actual_recipe_and_output_evidence(self):
        reference = {"sourceId": "minecraft:client_recipe_book", "generation": "d" * 64,
                     "recipeId": "openallay:client_recipe_display/17"}
        recipe = {"id": reference["recipeId"], "reference": reference,
                  "outputs": [{"stack": {"itemId": "minecraft:iron_block", "count": 1}}]}
        preview = {"recipe": recipe, "craftability": None, "ingredients": [], "sources": []}
        request = {"messages": [tool_message(recipe), tool_message(json.dumps(preview))]}
        content = fixture.manual_regression_content(request, 2)
        self.assertIn(reference["generation"], content)
        self.assertIn("本地验收阅读段 48", content)
        self.assertIn("全文末尾：原生图形长回复验收完成", content)
        self.assertIn('"type":"item_row"', content)
        self.assertIn('"itemId":"minecraft:iron_block"', content)
        self.assertIn("固定测试文案", content)

    def test_fixture_does_not_claim_results_before_the_two_actual_tools(self):
        self.assertIn("不是真实模型", fixture.manual_regression_content({}, 0))
        for completed in (1, 2):
            with self.subTest(completed=completed), self.assertRaises(ValueError):
                fixture.manual_regression_content({}, completed)


class CurrentModelProjectionTests(unittest.TestCase):
    def test_exact_actual_missing_native_recipe_stops_before_claiming_success(self):
        request = {"messages": [{"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                                 "content": ACTUAL_MISSING_NATIVE_RECIPE}]}
        with self.assertRaises(ValueError):
            fixture.manual_regression_content(request, 1)
        # The endpoint must fail immediately, not emit the old false recipe-returned text.
        request["messages"].insert(0, {"role": "user", "content": "OpenAllay E2E UI manual regressions"})
        class CaptureHandler:
            path = "/v1/chat/completions"
            body = json.dumps(request).encode()
            headers = {"content-length": str(len(body))}
            rfile = __import__("io").BytesIO(body)
            errors = []
            def send_error(self, code, message):
                self.errors.append((code, message))
        handler = CaptureHandler()
        fixture.Handler.do_POST(handler)
        self.assertEqual(422, handler.errors[0][0])
        self.assertIn("receipt", handler.errors[0][1])

    def test_exact_actual_null_capture_is_complete_but_not_a_recipe(self):
        self.assertEqual({"recipe": None, "craftability": None, "ingredients": [], "sources": []},
                         fixture.parse_result_preview(ACTUAL_NULL_RECIPE_COMPLETE_VIEW))
        request = {"messages": [{"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                                 "content": ACTUAL_NULL_RECIPE_COMPLETE_VIEW}]}
        with self.assertRaisesRegex(ValueError, "current recipe capture"):
            fixture.javascript_result(request)

    def test_labelled_preview_parser_keeps_nested_arrays_scalars_and_quoted_keys(self):
        rendered = ('flag: true\ncount: 2\nnothing: null\n'
                    '"key: with spaces": ' + json.dumps('value: "quoted"') + '\nemptyObject:\n  (empty object)\n'
                    'emptyArray:\n  (empty)\nrows:\n  -     name: "first"\n    values:\n'
                    '      - "minecraft:iron_ingot"\n      - 3\n  -     name: "second"\n'
                    '    values:\n      (empty)')
        self.assertEqual({"flag": True, "count": 2, "nothing": None,
                          "key: with spaces": 'value: "quoted"', "emptyObject": {}, "emptyArray": [],
                          "rows": [{"name": "first", "values": ["minecraft:iron_ingot", 3]},
                                   {"name": "second", "values": []}]},
                         fixture.parse_labelled_value(rendered))

    def test_native_sample_reference_and_complete_scalar_analysis_keep_actual_identity(self):
        recipe = captured_recipe()
        reference = recipe["reference"]
        # Exact current renderer layout. This unit shape is generated, not a claimed real run result.
        rendered = ('id: "' + recipe["id"] + '"\nreference:\n'
                    '  sourceId: "' + reference["sourceId"] + '"\n'
                    '  generation: "' + reference["generation"] + '"\n'
                    '  recipeId: "' + reference["recipeId"] + '"\noutputs:\n'
                    '  -     stack:\n      itemId: "' + fixture.RECIPE_OUTPUT + '"\n      count: 1')
        native = {"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                  "content": labelled_projection(rendered, complete=False)}
        with self.assertRaises(ValueError):
            fixture.parse_result_preview(native["content"])
        self.assertIn("正式工具决定", fixture.manual_regression_content({"messages": [native]}, 1))
        result = {"recipe": recipe, "craftability": {"craftable": True, "conclusive": False,
                  "requestedCrafts": 1, "maximumCrafts": 2}, "ingredients": [], "sources": []}
        scalar = {"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                  "content": labelled_projection(json.dumps(json.dumps(result)), "string")}
        request = {"messages": [native, scalar]}
        self.assertEqual(result, fixture.javascript_result(request))
        content = fixture.manual_regression_content(request, 2)
        self.assertIn(reference["recipeId"], content)
        self.assertIn(reference["generation"], content)
        self.assertIn('"conclusive":false', content)
        self.assertIn("全文末尾：原生图形长回复验收完成", content)
        result["recipe"]["reference"]["generation"] = "f" * 64
        scalar["content"] = labelled_projection(json.dumps(json.dumps(result)), "string")
        with self.assertRaisesRegex(ValueError, "first native recipe reference"):
            fixture.manual_regression_content(request, 2)

    def test_manual_endpoint_finishes_after_two_matching_current_tool_results(self):
        recipe = captured_recipe()
        result = {"recipe": recipe, "craftability": {"craftable": True, "conclusive": False,
                  "requestedCrafts": 1, "maximumCrafts": 2}, "ingredients": [], "sources": []}
        native = tool_message(recipe)
        native.pop("name")
        native["tool_call_id"] = "fixture-1"
        analysis = {"role": "tool", "tool_call_id": "fixture-2",
                    "content": labelled_projection(json.dumps(json.dumps(result)), "string")}
        request = {"messages": [
            {"role": "user", "content": "OpenAllay E2E UI manual regressions"},
            {"role": "assistant", "tool_calls": [{"id": "fixture-1", "type": "function",
             "function": {"name": fixture.JAVASCRIPT_TOOL, "arguments": "{}"}}]}, native,
            {"role": "assistant", "tool_calls": [{"id": "fixture-2", "type": "function",
             "function": {"name": fixture.JAVASCRIPT_TOOL, "arguments": "{}"}}]}, analysis]}
        class CaptureHandler:
            path = "/v1/chat/completions"
            body = json.dumps(request).encode()
            headers = {"content-length": str(len(body))}
            rfile = __import__("io").BytesIO(body)
            wfile = __import__("io").BytesIO()
            errors = []
            codes = []
            def send_error(self, code, message):
                self.errors.append((code, message))
            def send_response(self, code):
                self.codes.append(code)
            def send_header(self, name, value):
                pass
            def end_headers(self):
                pass
        handler = CaptureHandler()
        with patch.object(fixture.time, "sleep"):
            fixture.Handler.do_POST(handler)
        self.assertEqual([], handler.errors)
        self.assertEqual([200], handler.codes)
        events = [json.loads(line[6:]) for line in handler.wfile.getvalue().decode().splitlines()
                  if line.startswith("data: ") and line != "data: [DONE]"]
        text = "".join(event["choices"][0]["delta"].get("content", "") for event in events)
        self.assertIn("全文末尾：原生图形长回复验收完成", text)
        self.assertIn(recipe["reference"]["generation"], text)
        self.assertEqual("stop", events[-1]["choices"][0]["finish_reason"])

    def test_incomplete_ambiguous_malformed_and_wrong_tool_views_are_rejected(self):
        good = labelled_projection(json.dumps(json.dumps({"recipe": captured_recipe()})), "string")
        invalid = [good.replace("scope: complete", "scope: preview"),
                   good.replace("scope: complete", "scope: complete\nscope: preview"),
                   good.replace("type: string", "type: object"),
                   good.replace("preview:\n", "preview: (omitted by model output budget)\n"),
                   good.replace("r_test_1 (current request only)", "r_old (past request)"),
                   good.replace("next: answer", "status: failure\nnext: answer"),
                   "status: failure\ncode: javascript_error\nmessage: native failure"]
        for text in invalid:
            with self.subTest(text=text), self.assertRaises(ValueError):
                fixture.parse_result_preview(text)
        for malformed in ('field: 1\nfield: 2', 'field:\n    nested: 1',
                          'field: 1\n  nested: 2', 'field: "unterminated', 'field:\n', '\n'):
            with self.subTest(malformed=malformed), self.assertRaises(ValueError):
                fixture.parse_labelled_value(malformed)
        wrong = tool_message(json.dumps({"recipe": captured_recipe()}))
        wrong["name"] = "openallay__inspect_game_state"
        with self.assertRaises(ValueError):
            fixture.javascript_result({"messages": [wrong]})

    def test_http_fixture_rejects_internal_canonical_normalized_json(self):
        recipe = captured_recipe()
        # Internal canonical JSON is authoritative in the native probe, not the
        # current OpenAI HTTP Tool-result text interface consumed by this fixture.
        for preview in (recipe, json.dumps({"recipe": recipe})):
            canonical = json.dumps({"status": "success", "value": {"complete": True, "preview": preview}})
            with self.subTest(preview=preview), self.assertRaisesRegex(ValueError, "receipt"):
                fixture.parse_result_preview(canonical, require_complete=False)

    def test_captured_reference_source_id_output_and_count_are_strict(self):
        recipe = captured_recipe()
        for change in ({"generation": "…"}, {"sourceId": ""}, {"recipeId": "minecraft:iron_block"}):
            altered = json.loads(json.dumps(recipe))
            altered["reference"].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                fixture.captured_recipe_reference(altered)
        for count in (None, 0, -1, True, "1"):
            altered = json.loads(json.dumps(recipe))
            altered["outputs"][0]["stack"]["count"] = count
            with self.subTest(count=count), self.assertRaises(ValueError):
                fixture.captured_recipe_reference(altered)
        with patch.object(fixture, "RECIPE_SOURCE", "viewer:rei"), self.assertRaises(ValueError):
            fixture.captured_recipe_reference(recipe)
        with patch.object(fixture, "RECIPE_ID", "another:recipe"), self.assertRaises(ValueError):
            fixture.captured_recipe_reference(recipe)

    def test_only_current_user_turn_and_matching_openai_tool_call_are_consumed(self):
        recipe = captured_recipe()
        latest = tool_message(json.dumps({"recipe": recipe}))
        latest.pop("name")
        latest["tool_call_id"] = "fixture-2"
        request = {"messages": [
            tool_message(json.dumps({"recipe": captured_recipe("a" * 64)})),
            {"role": "user", "content": "OpenAllay E2E UI manual regressions"},
            {"role": "assistant", "tool_calls": [{"id": "fixture-2", "type": "function",
             "function": {"name": fixture.JAVASCRIPT_TOOL, "arguments": "{}"}}]}, latest]}
        self.assertEqual(recipe, fixture.javascript_result(request)["recipe"])
        request["messages"][2]["tool_calls"][0]["function"]["name"] = "other_tool"
        with self.assertRaises(ValueError):
            fixture.javascript_result(request)
        with self.assertRaises(ValueError):
            fixture.javascript_result({"messages": [tool_message(json.dumps({"recipe": recipe})),
                {"role": "user", "content": "new request"}]})


class RecipeProgramContractTests(unittest.TestCase):
    def test_both_programs_select_actual_output_and_optional_exact_id_source(self):
        selection = fixture.recipe_selection_source()
        self.assertTrue(fixture.manual_recipe_arguments()["source"].startswith(selection))
        self.assertTrue(fixture.javascript_arguments()["source"].startswith(selection))
        self.assertIn("candidate.outputs[outputIndex].stack.itemId", selection)
        self.assertNotIn("openallay:client_recipe_display/", selection)
        self.assertIn("return recipe;", fixture.manual_recipe_arguments()["source"])
        self.assertIn("return JSON.stringify({", fixture.javascript_arguments()["source"])
        self.assertEqual({"source", "title", "description"}, set(fixture.javascript_arguments()))
        with patch.object(fixture, "RECIPE_ID", "test:chosen"), patch.object(fixture, "RECIPE_SOURCE", "viewer:rei"):
            exact = fixture.recipe_selection_source()
            self.assertIn('candidate.id !== "test:chosen"', exact)
            self.assertIn('candidate.reference.sourceId !== "viewer:rei"', exact)

    @unittest.skipUnless(shutil.which("node"), "optional Node contract executor is not installed")
    def test_programs_use_current_recipe_inventory_and_real_crafting_module_shapes(self):
        # Node checks JS/data-shape semantics only. It does not certify Rhino/native graphical acceptance.
        recipe = captured_recipe()
        recipe.update({"ingredients": [{"key": f"input-{index}", "count": 1, "consumed": True,
                       "alternatives": [{"kind": "item", "id": "minecraft:iron_ingot",
                                         "resolvedItems": ["minecraft:iron_ingot"]}]} for index in range(9)],
                       "catalysts": [], "fluids": [], "evidence": {"completeness": "PARTIAL"}})
        wrong = captured_recipe(recipe_id="openallay:client_recipe_display/1")
        wrong["outputs"][0]["stack"]["itemId"] = "minecraft:gold_block"
        mc = {"recipes": [wrong, recipe], "player": {"inventory": {
            "slots": [{"slot": 0, "stack": {"itemId": "minecraft:iron_ingot", "count": 17}},
                      {"slot": 1, "stack": {"itemId": "minecraft:air", "count": 0}}],
            "offHand": {"itemId": "minecraft:iron_ingot", "count": 1},
            "evidence": {"completeness": "COMPLETE"}}}, "knowledge": []}
        payload = {"mc": mc, "native": fixture.manual_recipe_arguments()["source"],
                   "analysis": fixture.javascript_arguments()["source"], "module": str(
                       MODULE_PATH.parent.parent / "common/src/main/resources/assets/openallay/openallay_js_modules/crafting.js")}
        program = ("const input=JSON.parse(require('fs').readFileSync(0,'utf8'));"
                   "const mc=input.mc; const crafting=require(input.module);"
                   "const call=source=>new Function('mc','require',source)(mc,id=>{"
                   "if(id!=='openallay:crafting')throw new Error('wrong module');return crafting;});"
                   "const native=call(input.native);const answer=call(input.analysis);"
                   "console.log(JSON.stringify({same:native===mc.recipes[1],type:typeof answer,result:JSON.parse(answer)}));")
        result = subprocess.run([shutil.which("node"), "-e", program], input=json.dumps(payload),
                                text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        answer = json.loads(result.stdout)
        self.assertTrue(answer["same"])
        self.assertEqual("string", answer["type"])
        self.assertEqual(recipe, answer["result"]["recipe"])
        self.assertEqual(9, len(answer["result"]["ingredients"]))
        self.assertTrue(all(row["required"] == 1 and row["available"] == 18
                            for row in answer["result"]["ingredients"]))
        craftability = answer["result"]["craftability"]
        self.assertTrue(craftability["craftable"])
        self.assertFalse(craftability["conclusive"])
        self.assertEqual(2, craftability["maximumCrafts"])
        self.assertEqual(1, craftability["requestedCrafts"])
        self.assertEqual(recipe["reference"], fixture.captured_recipe_reference(answer["result"]["recipe"]))
        payload["mc"]["recipes"] = [wrong]
        missing = subprocess.run([shutil.which("node"), "-e", program], input=json.dumps(payload),
                                 text=True, capture_output=True, check=False)
        self.assertNotEqual(0, missing.returncode)
        self.assertIn("Current native recipe is unavailable", missing.stderr)


class BuilderFixtureTests(unittest.TestCase):
    def test_builder_is_explicit_and_unknown_phase_fails(self):
        self.assertIsNone(fixture.builder_scenario("ordinary build request"))
        self.assertEqual("acceptance", fixture.builder_scenario("OpenAllay E2E Builder acceptance"))
        with self.assertRaises(ValueError):
            fixture.builder_scenario("OpenAllay E2E Builder fabricated")

    def test_acceptance_loads_real_module_and_not_injected_backend(self):
        arguments = fixture.builder_arguments("acceptance")
        source = arguments["source"]
        self.assertNotIn("roots", arguments)
        self.assertIn('require("openallay_builder:building").open(', source)
        self.assertNotIn(".create(", source)
        for preset in ("simple_house", "skyscraper", "cottage", "windmill", "farm", "dock"):
            self.assertIn("b.build_" + preset + "(", source)
        for method in ("scan_terrain", "scan_ground", "build_smart_path", "scan_structure",
                       "save_template", "load_template", "paste_structure", "update_connections"):
            self.assertIn("b." + method + "(", source)
        self.assertIn('scenario:"builder_acceptance"', source)

    def test_skill_load_precedes_native_execution(self):
        call, content = fixture.builder_turn("acceptance", [])
        self.assertEqual((fixture.BUILDER_SKILL_TOOL, {"name": "minecraft-builder"}), call)
        self.assertIsNone(content)
        call, content = fixture.builder_turn("acceptance", [{"role": "tool", "content":
            "skill: minecraft-builder\nstate: complete\ncontent: actual extension Skill"}])
        self.assertEqual(fixture.JAVASCRIPT_TOOL, call[0])
        self.assertIn('require("openallay_builder:building")', call[1]["source"])

    def test_native_failure_never_becomes_pre_authored_success(self):
        skill = {"role": "tool", "content": "skill: minecraft-builder\nstate: complete"}
        result = {"role": "tool", "content": json.dumps({"status": "failure", "code": "javascript_error"})}
        with self.assertRaises(ValueError):
            fixture.builder_turn("acceptance", [skill, result])
        call, content = fixture.builder_turn("disabled", [skill, result])
        self.assertIsNone(call)
        self.assertIn("denied", content)
        self.assertIn("not a live model", content)
        wrong = {"role": "tool", "content": json.dumps({"status": "success", "value": {
            "preview": {"scenario": "builder_disabled", "unexpectedAuthority": True}}})}
        with self.assertRaises(ValueError):
            fixture.builder_turn("disabled", [skill, wrong])

    def test_disabled_continuation_accepts_actual_model_failure_projection_only(self):
        skill = {"role": "tool", "content": "skill: minecraft-builder\nstate: complete"}
        # Exact real 03-client ModelToolTextRenderer output. Not normalized JSON.
        projection = ('status: failure\ncode: javascript_error\nmessage: '
                      'ReferenceError: "Java" is not defined. '
                      '(openallay-module-openallay_builder:building.js#197)')
        result = {"role": "tool", "content": projection}
        call, content = fixture.builder_turn("disabled", [skill, result])
        self.assertIsNone(call)
        self.assertIn("denied", content)
        self.assertIn("No success is claimed", content)
        for invalid in (projection.replace("javascript_error", "other_failure"),
                        projection.replace("status: failure", "status: success"),
                        projection.replace('ReferenceError: "Java" is not defined.', "Error: invalid block"),
                        "noise\n" + projection):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                fixture.builder_turn("disabled", [skill, {"role": "tool", "content": invalid}])
        call, content = fixture.builder_turn("server-denied", [result])
        self.assertIsNone(call)
        self.assertIn("denied", content)

    def test_success_continuation_refers_to_controller_not_claimed_geometry(self):
        skill = {"role": "tool", "content": "skill: minecraft-builder\nstate: complete"}
        result = {"role": "tool", "content": 'result: handle\nscope: complete\npreview:\n'
            + json.dumps({"scenario": "builder_acceptance", "status": {"state": "completed"}})}
        call, content = fixture.builder_turn("acceptance", [skill, result])
        self.assertIsNone(call)
        self.assertIn("independent controller readback", content)
        self.assertIn("pre-authored", content)

    def test_ui_stop_runs_actual_cancellable_read_only_rhino_not_a_fake_result(self):
        arguments = fixture.ui_stop_arguments()
        self.assertNotIn("roots", arguments)
        self.assertIn("mc.player.uuid", arguments["source"])
        self.assertIn("while(true)", arguments["source"])
        for denied in ("Java", "commands", "building", "setBlock", "fetch", "Thread.sleep"):
            self.assertNotIn(denied, arguments["source"])
        self.assertTrue(arguments["title"])
        self.assertTrue(arguments["description"])
        self.assertNotIn("success", arguments["description"])

    def test_ui_failure_continuation_returns_actual_http_503(self):
        request = {"messages": [
            {"role": "user", "content": "OpenAllay E2E UI provider failure"},
            {"role": "tool", "content": "actual detached result"}]}
        class CaptureHandler:
            path = "/v1/chat/completions"
            body = json.dumps(request).encode()
            headers = {"content-length": str(len(body))}
            rfile = __import__("io").BytesIO(body)
            errors = []
            def send_error(self, code, message):
                self.errors.append((code, message))
        handler = CaptureHandler()
        fixture.Handler.do_POST(handler)
        self.assertEqual([(503, "Deterministic E2E continuation transport failure")], handler.errors)

    def test_ui_transport_failure_starts_with_actual_read_only_javascript(self):
        arguments = fixture.ui_provider_failure_arguments()
        self.assertNotIn("roots", arguments)
        self.assertEqual("return {player:mc.player};", arguments["source"])
        self.assertNotIn("Java", arguments["source"])
        self.assertNotIn("building", arguments["source"])
        self.assertTrue(arguments["title"])
        self.assertTrue(arguments["description"])

    def test_fixture_javascript_intents_describe_work_not_preclaimed_results(self):
        for scenario in fixture.BUILDER_SCENARIOS:
            arguments = fixture.builder_arguments(scenario, (-1, -61, 4) if scenario == "reload" else None)
            self.assertTrue(arguments["title"])
            self.assertTrue(arguments["description"])
            self.assertNotIn("85", arguments["description"])
            self.assertNotIn("PASSED", arguments["description"])
        recipe = fixture.javascript_arguments()
        self.assertTrue(recipe["title"])
        self.assertTrue(recipe["description"])

    def test_reload_uses_recorded_origin_not_moved_player_and_rejects_missing_origin(self):
        question = "OpenAllay E2E Builder reload\nE2E retained native anchor: x=-1,y=-61,z=4"
        anchor = fixture.builder_retained_anchor(question)
        self.assertEqual("reload", fixture.builder_scenario(question))
        self.assertEqual((-1, -61, 4), anchor)
        source = fixture.builder_arguments("reload", anchor)["source"]
        self.assertIn("x=-1;y=-61;z=4;", source)
        self.assertLess(source.index("x=-1;y=-61;z=4;"), source.index("var readback="))
        skill = {"role": "tool", "content": "skill: minecraft-builder\nstate: complete"}
        call, _ = fixture.builder_turn("reload", [skill], question)
        self.assertIn("x=-1;y=-61;z=4;", call[1]["source"])
        with self.assertRaises(ValueError):
            fixture.builder_arguments("reload")
        for invalid in ("OpenAllay E2E Builder reload", question + "\n" + question.splitlines()[1],
                        question.replace("y=-61", "y=-61.5"), question.replace("z=4", "z=2147483648")):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                fixture.builder_retained_anchor(invalid)

    def test_server_authority_probe_does_not_confuse_missing_module_with_java_denial(self):
        call, content = fixture.builder_turn("server-denied", [])
        self.assertEqual(fixture.JAVASCRIPT_TOOL, call[0])
        self.assertIn('Java.type("java.lang.System")', call[1]["source"])
        self.assertNotIn("BuilderRuntime", call[1]["source"])
        result = {"role": "tool", "content": json.dumps({"status": "failure", "code": "javascript_error"})}
        call, content = fixture.builder_turn("server-denied", [result])
        self.assertIsNone(call)
        self.assertIn("denied", content)

    def test_both_loader_opt_in_ticks_include_native_startup(self):
        root = MODULE_PATH.parent.parent
        for path in ("fabric/src/main/java/dev/openallay/fabric/OpenAllayFabricClient.java",
                     "neoforge/src/main/java/dev/openallay/neoforge/OpenAllayNeoForgeClient.java"):
            source = (root / path).read_text()
            self.assertIn("GuideClientE2EConfig.from(System.getProperties()).ifPresent", source)
            self.assertIn("controller.tick(client.player == null ? null : client.player.getUUID())", source)

    def test_lifecycle_programs_use_real_native_sessions(self):
        for scenario in ("partial", "cancel", "undo", "reload"):
            source = fixture.builder_arguments(scenario, (-1, -61, 4) if scenario == "reload" else None)["source"]
            self.assertIn('require("openallay_builder:building")', source)
            self.assertIn("building.open(", source)
            self.assertIn('scenario:"builder_' + scenario + '"', source)
        self.assertIn("b.cancel()", fixture.builder_arguments("cancel")["source"])
        self.assertIn("b.undo(original.operationId)", fixture.builder_arguments("undo")["source"])
        self.assertIn("b.load_template", fixture.builder_arguments("reload", (-1, -61, 4))["source"])


if __name__ == "__main__":
    unittest.main()
