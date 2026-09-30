#!/usr/bin/env python3
"""Deterministic loopback-only OpenAI-compatible fixture for real-client E2E."""

import argparse
import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


JAVASCRIPT_TOOL = "openallay__run_javascript"
GAME_STATE_STEPS = (
    ("openallay__inspect_game_state", {"section": "OVERVIEW", "query": "summary"}),
    ("openallay__inspect_game_state", {"section": "MODS", "query": "list"}),
    ("openallay__inspect_game_state", {"section": "OPTIONS", "query": "groups"}),
    ("openallay__inspect_game_state", {"section": "PACKS", "query": "summary"}),
    ("openallay__inspect_game_state", {"section": "SHADERS", "query": "summary"}),
    ("openallay__inspect_game_state", {"section": "DIAGNOSTICS", "query": "categories"}),
    ("openallay__inspect_game_state", {"section": "PLAYER", "query": "summary"}),
    ("openallay__inspect_game_state", {"section": "WORLD_QUERY", "query": "time"}),
)

RECIPE_OUTPUT = os.environ.get(
    "OPENALLAY_E2E_RECIPE_OUTPUT", "minecraft:iron_block")
RECIPE_ID = os.environ.get(
    "OPENALLAY_E2E_RECIPE_ID", "minecraft:iron_block")
RECIPE_LABEL = os.environ.get("OPENALLAY_E2E_RECIPE_LABEL", "铁块")
RECIPE_SOURCE = os.environ.get("OPENALLAY_E2E_RECIPE_SOURCE")


def javascript_arguments():
    """Ask the production JavaScript Tool to inspect only current captured roots."""
    recipe_id = json.dumps(RECIPE_ID)
    output_id = json.dumps(RECIPE_OUTPUT)
    source = f'''var candidates = [];
for (var recipeIndex = 0; recipeIndex < mc.recipes.length; recipeIndex++) {{
  var candidate = mc.recipes[recipeIndex];
  if (candidate.id !== {recipe_id}) continue;
  var matchingOutput = false;
  for (var outputIndex = 0; outputIndex < candidate.outputs.length; outputIndex++) {{
    if (candidate.outputs[outputIndex].stack.itemId === {output_id}) {{
      matchingOutput = true;
      break;
    }}
  }}
  if (matchingOutput) candidates.push(candidate);
}}
var recipe = candidates.length ? candidates[0] : null;
var counts = {{}};
for (var slotIndex = 0; slotIndex < mc.player.inventory.slots.length; slotIndex++) {{
  var stack = mc.player.inventory.slots[slotIndex].stack;
  if (stack.count > 0 && stack.itemId !== "minecraft:air") {{
    counts[stack.itemId] = (counts[stack.itemId] || 0) + Number(stack.count);
  }}
}}
var offHand = mc.player.inventory.offHand;
if (offHand.count > 0 && offHand.itemId !== "minecraft:air") {{
  counts[offHand.itemId] = (counts[offHand.itemId] || 0) + Number(offHand.count);
}}
var ingredients = [];
if (recipe) {{
  for (var ingredientIndex = 0; ingredientIndex < recipe.ingredients.length; ingredientIndex++) {{
    var requirement = recipe.ingredients[ingredientIndex];
    var itemId = null;
    for (var alternativeIndex = 0; alternativeIndex < requirement.alternatives.length; alternativeIndex++) {{
      var alternative = requirement.alternatives[alternativeIndex];
      if (alternative.resolvedItems.length) {{
        itemId = alternative.resolvedItems[0];
        break;
      }}
      if (alternative.kind === "item") {{
        itemId = alternative.id;
        break;
      }}
    }}
    if (itemId !== null) ingredients.push({{
      itemId: itemId,
      required: Number(requirement.count),
      available: Number(counts[itemId] || 0)
    }});
  }}
}}
var sourcesById = {{}};
for (var knowledgeIndex = 0; knowledgeIndex < mc.knowledge.length; knowledgeIndex++) {{
  var document = mc.knowledge[knowledgeIndex];
  sourcesById[document.sourceId] = true;
}}
var sources = Object.keys(sourcesById).sort().map(function (sourceId) {{
  return {{sourceId: sourceId}};
}});
return {{
  recipe: recipe,
  craftability: recipe
    ? require("openallay:crafting").allocate(recipe, mc.player.inventory, 1)
    : null,
  ingredients: ingredients,
  sources: sources
}};
'''
    return {
        "source": source,
        "roots": ["recipes", "player", "knowledge"],
    }


def javascript_result(request):
    for message in reversed(request.get("messages", [])):
        if message.get("role") != "tool":
            continue
        try:
            normalized = json.loads(message.get("content", ""))
            value = normalized.get("value", {})
            preview = value.get("preview", value)
            if (normalized.get("status") == "success"
                    and isinstance(preview, dict)
                    and isinstance(preview.get("recipe"), dict)):
                return preview
        except (TypeError, json.JSONDecodeError):
            continue
    raise ValueError("run_javascript result did not contain current recipe capture")


def recipe_reference(request):
    result = javascript_result(request)
    recipe = result.get("recipe")
    reference = recipe.get("reference") if isinstance(recipe, dict) else None
    if not isinstance(reference, dict):
        raise ValueError("current recipe capture did not contain an exact reference")
    if RECIPE_SOURCE is not None and reference.get("sourceId") != RECIPE_SOURCE:
        raise ValueError("current recipe reference did not match the selected source")
    return {key: reference[key] for key in ("sourceId", "generation", "recipeId")}


def assistant_content(request, completed):
    if completed == 0:
        return ("# Current-client E2E fixture\n\n"
                "This deterministic loopback fixture uses pre-authored responses; "
                "it is not a live or representative model. It will call the current "
                "`openallay:run_javascript` Tool once, using the recipe Skill and "
                "the request's detached capture, then render the returned current "
                "recipe reference.")

    result = javascript_result(request)
    recipe = result["recipe"]
    if not isinstance(recipe, dict):
        raise ValueError("selected recipe was not present in the current capture")
    reference = recipe_reference(request)
    content = ["## 当前捕获的配方\n\n"
               "配方、制作检查和库存材料来自本次 `run_javascript` 结果；"
               "组件只引用本次捕获中的精确配方。\n\n"]
    component = {
        "schemaVersion": 1, "type": "recipe_grid",
        "properties": {**reference, "label": RECIPE_LABEL + "配方"},
        "fallback": RECIPE_LABEL + "配方已从当前捕获读取",
        "narration": RECIPE_LABEL + "配方来自当前请求捕获",
    }
    content.extend(["```openallay-component\n",
                    json.dumps(component, ensure_ascii=False, separators=(",", ":")),
                    "\n```\n\n"])
    ingredients = result.get("ingredients", [])
    if ingredients:
        ingredient_component = {
            "schemaVersion": 1, "type": "ingredient_check",
            "properties": {"ingredients": [
                {**ingredient,
                 "label": ingredient["itemId"].split(":", 1)[-1].replace("_", " ")}
                for ingredient in ingredients]},
            "fallback": "材料对照来自当前捕获的配方和玩家背包",
            "narration": "显示当前捕获的配方材料和库存对照",
        }
        content.extend(["```openallay-component\n",
                        json.dumps(ingredient_component, ensure_ascii=False,
                                   separators=(",", ":")), "\n```\n\n"])
    craftability = result.get("craftability")
    if isinstance(craftability, dict):
        craft_component = {
            "schemaVersion": 1, "type": "craftability_summary",
            "properties": {**reference,
                           **{key: craftability[key] for key in (
                               "craftable", "conclusive", "requestedCrafts", "maximumCrafts")}},
            "fallback": "制作能力按当前捕获的配方和库存计算",
            "narration": "显示确定性制作检查结果",
        }
        content.extend(["```openallay-component\n",
                        json.dumps(craft_component, ensure_ascii=False,
                                   separators=(",", ":")), "\n```\n\n"])
    sources = result.get("sources", [])
    if sources:
        source_component = {
            "schemaVersion": 1, "type": "source_summary",
            "properties": {"sources": [
                {"sourceId": source["sourceId"],
                 "label": source_label(source["sourceId"])}
                for source in sources if isinstance(source, dict)
                and isinstance(source.get("sourceId"), str)]},
            "fallback": "当前捕获的知识来源",
            "narration": "显示当前捕获的知识来源",
        }
        if source_component["properties"]["sources"]:
            content.extend(["```openallay-component\n",
                            json.dumps(source_component, ensure_ascii=False,
                                       separators=(",", ":")), "\n```\n\n"])
    content.append("此 loopback 响应是固定验收文本，不代表真实模型生成。")
    return "".join(content)


def source_label(source_id):
    return {
        "patchouli:resources": "资源指南",
        "viewer:jei": "JEI 配方来源",
        "viewer:rei": "REI 配方来源",
    }.get(source_id, "游戏内知识来源")


def game_state_assistant_content(completed, world_query_permission_denied=False):
    labels = (
        "运行概览", "已安装模组", "设置分组", "资源包与数据包",
        "光影状态", "F3 诊断类别", "玩家可见状态", "只读世界时间",
    )
    if completed < len(labels):
        return "## 游戏外层状态验收\n\n正在读取：**" + labels[completed] + "**。"
    world_query_line = (
        "- 只读世界查询明确返回权限不足；Agent 保留该结构化失败并继续完成"
        if world_query_permission_denied
        else "- 已读取只读世界查询"
    )
    return f"""
## 游戏外层状态验收完成

- 已读取运行环境和安装模组
- 已读取设置、资源包、数据包与光影集成状态
- 已读取 F3 类诊断和玩家可见状态
{world_query_line}

所有结果来自同一请求开始时脱离 Minecraft 对象的只读快照；不可用或不完整部分保持明确标注。
""".strip()


def validated_game_state_results(turn_messages, allow_world_query_permission_failure=False):
    expected = [arguments["section"] for _, arguments in GAME_STATE_STEPS]
    observed = []
    world_query_permission_denied = False
    for message in turn_messages:
        if message.get("role") != "tool":
            continue
        try:
            result = json.loads(message.get("content", ""))
            if result.get("status") != "success":
                if (allow_world_query_permission_failure
                        and len(observed) == len(expected) - 1
                        and result.get("code") == "permission_denied"):
                    observed.append(expected[-1])
                    world_query_permission_denied = True
                    continue
                raise ValueError("game-state tool returned failure")
            section = result["value"]["section"]
        except (KeyError, TypeError, json.JSONDecodeError) as failure:
            raise ValueError("game-state tool result is malformed") from failure
        if len(observed) >= len(expected) or section != expected[len(observed)]:
            raise ValueError("game-state tool result section is out of order")
        observed.append(section)
    return observed, world_query_permission_denied




BUILDER_PREFIX = "OpenAllay E2E Builder "
BUILDER_SCENARIOS = ("disabled", "acceptance", "reload", "partial", "cancel", "undo", "server-denied")
BUILDER_SKILL_TOOL = "openallay__load_skill"


def builder_scenario(user_text):
    if not user_text.startswith(BUILDER_PREFIX):
        return None
    scenario = user_text[len(BUILDER_PREFIX):].strip().split(None, 1)[0]
    if scenario not in BUILDER_SCENARIOS:
        raise ValueError("unknown deterministic Builder scenario: " + scenario)
    return scenario


def builder_retained_anchor(user_text):
    """Recorded native coordinates supplied by the controller, never an answer."""
    import re
    lines = [line for line in user_text.splitlines()
             if line.startswith("E2E retained native anchor:")]
    if len(lines) != 1:
        raise ValueError("reload requires one exact retained native anchor line")
    match = re.fullmatch(r"E2E retained native anchor: x=(-?\d+),y=(-?\d+),z=(-?\d+)", lines[0])
    if match is None:
        raise ValueError("retained native anchor line is malformed")
    anchor = tuple(int(value) for value in match.groups())
    if any(value < -2147483648 or value > 2147483647 for value in anchor):
        raise ValueError("retained native anchor coordinate is outside signed 32-bit bounds")
    return anchor


def builder_arguments(scenario, retained_anchor=None):
    """Actual Extension/native programs, not fixture backends or claimed geometry."""
    if scenario == "acceptance":
        from pathlib import Path
        source = Path(__file__).with_name("e2e-builder-fixture.js").read_text(encoding="utf-8")
    elif scenario == "server-denied":
        source = ('var System = Java.type("java.lang.System");\n'
                  'return {unexpectedJavaAuthority:true,version:String(System.getProperty("java.version"))};')
    elif scenario == "disabled":
        source = ('var building = require("openallay_builder:building");\n'
                  'var b = building.open({seed:17,label:"OpenAllay E2E denied"});\n'
                  'var p = b.get_player_pos();\n'
                  'b.place_block(Math.floor(p.x)+8,Math.floor(p.y),Math.floor(p.z)+8,"gold_block");\n'
                  'return {unexpectedAuthority:true,status:b.finish()};')
    else:
        source = '''var building = require("openallay_builder:building");
var b = building.open({seed:17,label:"OpenAllay E2E SCENARIO"});
var p = b.get_player_pos();
var x=Math.floor(p.x)+8,y=Math.floor(p.y)-1,z=Math.floor(p.z)+8;
'''.replace("SCENARIO", scenario)
        if scenario == "partial":
            source += '''b.place_block(x,y+1,z,"gold_block");
var caught=null;
try { b.place_block(x+1,y+1,z,"openallay_e2e:missing_native_block"); }
catch (error) { caught=String(error); }
return {scenario:"builder_partial",failure:caught,status:b.status(),operations:b.list_operations()};
'''
        elif scenario == "cancel":
            source += '''b.place_block(x,y+1,z,"diamond_block");
var cancelled=b.cancel(),caught=null;
try { b.place_block(x+1,y+1,z,"gold_block"); }
catch (error) { caught=String(error); }
return {scenario:"builder_cancel",deniedAfterCancel:caught,status:cancelled};
'''
        elif scenario == "undo":
            source += '''b.place_block(x,y+1,z,"gold_block");
b.place_block(x+1,y+1,z,"gold_block");
var original=b.finish();
var changed=building.open({seed:17,label:"OpenAllay E2E intervening edit"});
changed.place_block(x+1,y+1,z,"diamond_block");
changed.finish();
var undo=b.undo(original.operationId);
return {scenario:"builder_undo",undo:undo,status:b.finish()};
'''
        elif scenario == "reload":
            if retained_anchor is None or len(retained_anchor) != 3:
                raise ValueError("reload requires the independently retained native origin")
            source += "x=%d;y=%d;z=%d;\n" % tuple(retained_anchor)
            source += '''var template=b.load_template("openallay_e2e_builder_native");
var listed=b.list_templates();
var operations=b.list_operations();
var readback={house:b.get_block(x,y,z),dock:b.get_block(x+14,y,z+18),
  rotatedStair:b.get_block_full(x+21,y+1,z+32),mirroredChest:b.get_block_full(x+24,y+1,z+33)};
return {scenario:"builder_reload",template:{name:"openallay_e2e_builder_native",size:template.size},
  listed:listed,operations:operations,operationCount:operations.length,readback:readback,status:b.finish()};
'''
    return {"source": source, "roots": ["player"]}


def builder_turn(scenario, turn_messages, user_text=""):
    """Validate the real current Tool results; never manufacture acceptance."""
    results = [message for message in turn_messages if message.get("role") == "tool"]
    if not results:
        if scenario == "server-denied":
            return (JAVASCRIPT_TOOL, builder_arguments(scenario)), None
        return (BUILDER_SKILL_TOOL, {"name": "minecraft-builder"}), None
    # load_skill has a compact text model projection, not always JSON.
    if scenario != "server-denied":
        skill_text = str(results[0].get("content", ""))
        if "minecraft-builder" not in skill_text or "failure" in skill_text[:100]:
            raise ValueError("bundled Builder Skill was not loaded")
        if len(results) == 1:
            anchor = builder_retained_anchor(user_text) if scenario == "reload" else None
            return (JAVASCRIPT_TOOL, builder_arguments(scenario, anchor)), None
    if len(results) != (1 if scenario == "server-denied" else 2):
        raise ValueError("unexpected extra Builder fixture Tool result")
    text = results[-1].get("content", "")
    parsed = None
    try:
        parsed = json.loads(text)
    except (TypeError, json.JSONDecodeError):
        pass
    denied = scenario in ("disabled", "server-denied")
    if isinstance(parsed, dict):
        if denied:
            if parsed.get("status") != "failure" or parsed.get("code") != "javascript_error":
                raise ValueError("native Builder denial did not return actual JavaScript failure")
            summary = "Native Builder access was denied by the real JavaScript Tool. No success is claimed."
        else:
            if parsed.get("status") != "success":
                raise ValueError("native Builder fixture failed: " + str(parsed.get("code")))
            output = parsed.get("value", {})
            preview = output.get("preview", output) if isinstance(output, dict) else {}
            if not isinstance(preview, dict) or preview.get("scenario") != "builder_" + scenario.replace("-", "_"):
                raise ValueError("native Builder result did not identify the requested scenario")
            summary = "Native Tool returned the requested " + scenario + " result. The independent controller readback determines acceptance."
    else:
        # ModelToolTextRenderer renders failures as exact named text fields. The
        # canonical normalized failure remains independently checked by the controller.
        if denied:
            prefix = "status: failure\ncode: javascript_error\nmessage: "
            if not isinstance(text, str) or not text.startswith(prefix):
                raise ValueError("native Builder denial projection lacked exact failure status/code")
            message = text[len(prefix):]
            if not message.startswith('ReferenceError: "Java" is not defined.'):
                raise ValueError("native Builder denial did not show unavailable Java authority")
            summary = "Native Builder access was denied by the real JavaScript Tool. No success is claimed."
        else:
            # Complete unrestricted results contain the actual JSON after preview.
            if "scope: complete" not in text or '"scenario":"builder_' + scenario.replace("-", "_") + '"' not in text.replace(" ", ""):
                raise ValueError("native Builder projection was incomplete or malformed")
            summary = "Native Tool returned the requested " + scenario + " result. The independent controller readback determines acceptance."
    return None, ("# Deterministic Builder real-client fixture\n\n" + summary +
                  "\n\nThis loopback response is explicitly pre-authored test content, not a live model. "
                  "It invokes the actual bundled Extension. It does not certify its own geometry or visual quality.")


def content_events(content):
    # Fixed small chunks deliberately split Markdown and component tokens.
    chunks = [content[index:index + 17] for index in range(0, len(content), 17)]
    return [{"content": chunk} for chunk in chunks]


def current_user_turn(request):
    messages = request.get("messages", [])
    latest_user = -1
    user_text = ""
    for index, message in enumerate(messages):
        if message.get("role") == "user":
            latest_user = index
            user_text = message.get("content", "")
    return user_text, messages[latest_user + 1:]


class Handler(BaseHTTPRequestHandler):
    server_version = "OpenAllayFixture/1"

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return
        length = int(self.headers.get("content-length", "0"))
        request = json.loads(self.rfile.read(length))
        user_text, turn_messages = current_user_turn(request)
        completed = sum(1 for message in turn_messages
                        if message.get("role") == "tool")
        history_seed = user_text.startswith("OpenAllay E2E 历史分页种子 ")
        server_client_tools = user_text.startswith(
            "OpenAllay E2E 服务端模型反向工具验收")
        game_state = (user_text.startswith("OpenAllay E2E 游戏外层状态验收")
                      or server_client_tools)
        world_query_permission_denied = False
        try:
            builder = builder_scenario(user_text)
            builder_step, builder_content = builder_turn(builder, turn_messages, user_text) if builder else (None, None)
        except ValueError as failure:
            self.send_error(422, str(failure))
            return
        if game_state:
            try:
                observed, world_query_permission_denied = validated_game_state_results(
                    turn_messages, True)
                completed = len(observed)
            except ValueError as failure:
                self.send_error(422, str(failure))
                return
        try:
            content = (
                (builder_content or "# Deterministic Builder real-client fixture\n\nPre-authored loopback provider. Calling the real bundled Extension through the production Tool; no live-model claim.") if builder
                else "历史分页种子已记录。" if history_seed
                else "服务端模型已完成客户端状态读取；无权限的只读世界查询作为工具失败返回后，Agent 仍正常完成。"
                if server_client_tools and completed == len(GAME_STATE_STEPS)
                else game_state_assistant_content(
                    completed, world_query_permission_denied) if game_state
                else assistant_content(request, completed))
        except ValueError as failure:
            self.send_error(422, str(failure))
            return
        deltas = content_events(content)
        steps = GAME_STATE_STEPS if game_state else (JAVASCRIPT_TOOL,)
        if builder_step is not None or (not builder and not history_seed and completed < len(steps)):
            try:
                name, arguments = (builder_step if builder
                                   else steps[completed] if game_state
                                   else (JAVASCRIPT_TOOL, javascript_arguments()))
            except ValueError as failure:
                self.send_error(422, str(failure))
                return
            available = {tool["function"]["name"]
                         for tool in request.get("tools", [])}
            if name not in available:
                self.send_error(422, "required E2E tool unavailable: " + name)
                return
            deltas.append({"tool_calls": [{
                "index": 0,
                "id": "fixture-" + str(completed + 1),
                "type": "function",
                "function": {"name": name, "arguments": json.dumps(arguments)},
            }]})
            reason = "tool_calls"
        else:
            reason = "stop"
        events = []
        for index, delta in enumerate(deltas):
            event = {
                "id": "openallay-fixture",
                "model": "openallay-e2e-fixture",
                "choices": [{
                    "index": 0,
                    "delta": delta,
                    "finish_reason": reason if index == len(deltas) - 1 else None,
                }],
            }
            if index == len(deltas) - 1:
                event["usage"] = {"prompt_tokens": 10, "completion_tokens": 4}
            events.append("data: " + json.dumps(
                event, ensure_ascii=False, separators=(",", ":")) + "\n\n")
        body = ("".join(events) + "data: [DONE]\n\n").encode()
        # Keep one real-client render window open long enough for the native progress strip
        # to be captured. This is loopback-only deterministic fixture latency.
        if not history_seed:
            time.sleep(0.35)
        self.send_response(200)
        self.send_header("content-type", "text/event-stream")
        self.send_header("cache-control", "no-store")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, pattern, *args):
        print("fixture:", pattern % args, flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18765)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"OpenAllay E2E model fixture listening on 127.0.0.1:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
