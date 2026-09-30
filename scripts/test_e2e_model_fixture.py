import importlib.util
import json
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("e2e-model-fixture.py")
spec = importlib.util.spec_from_file_location("e2e_model_fixture", MODULE_PATH)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


class JavascriptFixtureTests(unittest.TestCase):
    def test_default_request_uses_current_javascript_tool_only(self):
        arguments = fixture.javascript_arguments()
        self.assertEqual(["recipes", "player", "knowledge"], arguments["roots"])
        self.assertIn("mc.recipes", arguments["source"])
        self.assertIn('require("openallay:crafting").allocate', arguments["source"])
        self.assertNotIn("search_recipes", arguments["source"])
        self.assertEqual("openallay__run_javascript", fixture.JAVASCRIPT_TOOL)

    def test_result_requires_current_tool_success_and_recipe_reference(self):
        recipe = {"id": fixture.RECIPE_ID,
                  "reference": {"sourceId": "minecraft:recipe_manager",
                                "generation": "a" * 64,
                                "recipeId": fixture.RECIPE_ID}}
        request = {"messages": [
            {"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
             "content": json.dumps({"status": "success", "value": {
                 "preview": {"recipe": recipe, "craftability": None,
                             "ingredients": [], "sources": []}}})}
        ]}
        self.assertEqual(recipe["reference"], fixture.recipe_reference(request))
        self.assertEqual(recipe, fixture.javascript_result(request)["recipe"])

    def test_fails_closed_when_tool_result_has_no_captured_recipe(self):
        request = {"messages": [{"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                                  "content": json.dumps({"status": "failure",
                                                         "code": "missing_recipe"})}]}
        with self.assertRaises(ValueError):
            fixture.assistant_content(request, 1)

    def test_component_output_uses_the_exact_captured_reference_and_values(self):
        reference = {"sourceId": "minecraft:recipe_manager",
                     "generation": "b" * 64, "recipeId": fixture.RECIPE_ID}
        preview = {"recipe": {"reference": reference},
                   "craftability": {"craftable": True, "conclusive": True,
                                    "requestedCrafts": 1, "maximumCrafts": 2},
                   "ingredients": [{"itemId": "minecraft:iron_ingot", "required": 1,
                                    "available": 3}],
                   "sources": [{"sourceId": "patchouli:resources"}]}
        request = {"messages": [{"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                                  "content": json.dumps({"status": "success", "value": {
                                      "preview": preview}})}]}
        content = fixture.assistant_content(request, 1)
        self.assertIn(reference["generation"], content)
        self.assertIn('"craftable":true', content)
        self.assertIn('"available":3', content)
        self.assertIn("固定验收文本，不代表真实模型生成", content)


class BuilderFixtureTests(unittest.TestCase):
    def test_builder_is_explicit_and_unknown_phase_fails(self):
        self.assertIsNone(fixture.builder_scenario("ordinary build request"))
        self.assertEqual("acceptance", fixture.builder_scenario("OpenAllay E2E Builder acceptance"))
        with self.assertRaises(ValueError):
            fixture.builder_scenario("OpenAllay E2E Builder fabricated")

    def test_acceptance_loads_real_module_and_not_injected_backend(self):
        arguments = fixture.builder_arguments("acceptance")
        source = arguments["source"]
        self.assertEqual(["player"], arguments["roots"])
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
        self.assertEqual(["player"], arguments["roots"])
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
        self.assertEqual(["player"], arguments["roots"])
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
