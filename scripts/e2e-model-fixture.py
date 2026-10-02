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
# Client recipe-book IDs are request-captured display IDs, not registry recipe IDs.
# A caller may require an exact ID, but the default selects by actual output.
RECIPE_ID = os.environ.get("OPENALLAY_E2E_RECIPE_ID")
RECIPE_LABEL = os.environ.get("OPENALLAY_E2E_RECIPE_LABEL", "铁块")
RECIPE_SOURCE = os.environ.get("OPENALLAY_E2E_RECIPE_SOURCE")


def recipe_selection_source():
    """Select an existing closed host recipe; never invent its ID or reference."""
    recipe_id = json.dumps(RECIPE_ID)
    output_id = json.dumps(RECIPE_OUTPUT)
    source_id = json.dumps(RECIPE_SOURCE)
    return f'''var recipe = null;
for (var recipeIndex = 0; recipeIndex < mc.recipes.length; recipeIndex++) {{
  var candidate = mc.recipes[recipeIndex];
  if ({recipe_id} !== null && candidate.id !== {recipe_id}) continue;
  if ({source_id} !== null && candidate.reference.sourceId !== {source_id}) continue;
  var matchingOutput = false;
  for (var outputIndex = 0; outputIndex < candidate.outputs.length; outputIndex++) {{
    if (candidate.outputs[outputIndex].stack.itemId === {output_id}
        && candidate.outputs[outputIndex].stack.count > 0) {{
      matchingOutput = true;
      break;
    }}
  }}
  if (matchingOutput) {{
    recipe = candidate;
    break;
  }}
}}
if (recipe === null) throw new Error("Current native recipe is unavailable");
'''


def javascript_arguments():
    """Ask the production JavaScript Tool to read current captured data lazily."""
    source = recipe_selection_source() + f'''var counts = {{}};
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
// A scalar JSON answer preserves all computed rows in the model projection.
// Returning the closed host recipe itself is reserved for the first native card Tool.
return JSON.stringify({{
  recipe: recipe,
  craftability: require("openallay:crafting").allocate(recipe, mc.player.inventory, 1),
  ingredients: ingredients,
  sources: sources
}});
'''
    return {
        "source": source,
        "title": "读取当前配方与背包",
        "description": "用本次捕获的配方、背包和知识来源计算材料对照与制作能力。",
    }


def parse_result_preview(text, require_complete=True):
    """Read only the current labelled model projection.

    The first closed native recipe is naturally sampled for model display. Its
    exact reference/output must still be present; the frozen probe verifies the
    complete canonical value and trusted native view independently.
    """
    if not isinstance(text, str):
        raise ValueError("run_javascript result is not text")
    import re
    header, separator, remaining = text.partition("\npreview:\n")
    receipt, schema_separator, schema = header.partition("\nschema:\n")
    match = re.fullmatch(
        r"result: \S+ \(current request only\)\ntype: (object|array|string|number|boolean|null)"
        r"\ncardinality: [0-9]+\nsize: [0-9]+ UTF-8 byte\(s\)\nscope: (complete|preview)",
        receipt)
    if not match or not schema_separator or not schema or not separator:
        raise ValueError("run_javascript projection receipt is malformed")
    scope = match[2]
    ending = (("\nnext: answer from this complete result; "
               "do not call run_javascript again only to verify it")
              if scope == "complete" else "\nomitted: ")
    preview, next_separator, tail = remaining.partition(ending)
    if (not next_separator or require_complete and scope != "complete"
            or scope == "preview" and not re.match(
                r"[0-9]+ row\(s\), [0-9]+ field\(s\) or nested value\(s\); ", tail)):
        raise ValueError("run_javascript projection is not the required success view")
    value = parse_labelled_value(preview)
    actual_type = ("null" if value is None else "boolean" if isinstance(value, bool)
                   else "number" if isinstance(value, (int, float))
                   else "object" if isinstance(value, dict)
                   else "array" if isinstance(value, list) else "string")
    if actual_type != match[1]:
        raise ValueError("run_javascript preview did not match its declared type")
    return value


def parse_labelled_value(text):
    """Parse JsonResultProjection.render's indentation, keys and JSON scalars."""
    lines = []
    for line in text.splitlines():
        if not line.strip():
            raise ValueError("run_javascript preview contains an empty line")
        lines.append((len(line) - len(line.lstrip(" ")), line.lstrip(" ")))

    def scalar(value):
        if value == "(empty)":
            return []
        if value == "(empty object)":
            return {}
        try:
            return json.loads(value)
        except json.JSONDecodeError as failure:
            raise ValueError("run_javascript preview scalar is malformed") from failure

    def field(value):
        # Quoted keys may contain colons; unquoted names may contain namespace colons.
        if value.startswith('"'):
            try:
                key, end = json.JSONDecoder().raw_decode(value)
            except json.JSONDecodeError as failure:
                raise ValueError("run_javascript preview field is malformed") from failure
            tail = value[end:]
            if not tail:
                return None
            if not isinstance(key, str) or not tail.startswith(":"):
                raise ValueError("run_javascript preview field is malformed")
            return key, tail[1:].lstrip(" ")
        import re
        match = re.fullmatch(r"(.+?):(?: (.*))?", value)
        return (match[1], match[2] or "") if match else None

    def parse(rows):
        if not rows:
            raise ValueError("run_javascript preview is empty")
        indent, first = rows[0]
        if first.startswith("- "):
            result = []
            index = 0
            while index < len(rows):
                level, row = rows[index]
                if level != indent or not row.startswith("- "):
                    raise ValueError("run_javascript preview array indentation is malformed")
                end = index + 1
                while end < len(rows) and rows[end][0] > indent:
                    end += 1
                # The renderer pads the first object field after '- '; its logical
                # indentation is still exactly two spaces beyond the array itself.
                child = [(indent + 2, row[2:].lstrip(" "))] + rows[index + 1:end]
                result.append(parse(child))
                index = end
            return result
        if field(first) is None:
            if len(rows) != 1:
                raise ValueError("run_javascript preview scalar has nested fields")
            return scalar(first)
        result = {}
        index = 0
        while index < len(rows):
            level, row = rows[index]
            entry = field(row)
            if level != indent or entry is None:
                raise ValueError("run_javascript preview object indentation is malformed")
            key, value = entry
            if key in result:
                raise ValueError("run_javascript preview contains a duplicate field")
            end = index + 1
            while end < len(rows) and rows[end][0] > indent:
                end += 1
            if value:
                if end != index + 1:
                    raise ValueError("run_javascript preview scalar has nested fields")
                result[key] = scalar(value)
            else:
                if end == index + 1 or rows[index + 1][0] != indent + 2:
                    raise ValueError("run_javascript preview nested field is malformed")
                result[key] = parse(rows[index + 1:end])
            index = end
        return result

    if not lines or lines[0][0] != 0:
        raise ValueError("run_javascript preview root indentation is malformed")
    return parse(lines)


def current_tool_results(request):
    _, messages = current_user_turn(request)
    calls = {}
    results = []
    for message in messages:
        if message.get("role") == "assistant":
            for call in message.get("tool_calls", []):
                if call.get("id") in calls:
                    raise ValueError("current request contains a duplicate Tool call ID")
                calls[call.get("id")] = call.get("function", {}).get("name")
        if message.get("role") != "tool":
            continue
        name = message.get("name")
        if name is None:
            name = calls.get(message.get("tool_call_id"))
        if name != JAVASCRIPT_TOOL:
            raise ValueError("current result is not from the requested JavaScript Tool")
        results.append(message)
    return results


def javascript_result(request):
    results = current_tool_results(request)
    preview = parse_result_preview(results[-1].get("content", "")) if results else None
    if isinstance(preview, str):
        try:
            preview = json.loads(preview)
        except json.JSONDecodeError as failure:
            raise ValueError("run_javascript scalar answer is not JSON") from failure
    if not isinstance(preview, dict) or not isinstance(preview.get("recipe"), dict):
        raise ValueError("run_javascript result did not contain current recipe capture")
    return preview


def captured_recipe_reference(recipe):
    import re
    reference = recipe.get("reference") if isinstance(recipe, dict) else None
    if (not isinstance(reference, dict)
            or set(reference) != {"sourceId", "generation", "recipeId"}
            or not isinstance(reference["sourceId"], str)
            or not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", reference["sourceId"])
            or not isinstance(reference["generation"], str)
            or not re.fullmatch(r"[0-9a-f]{64}", reference["generation"])
            or not isinstance(reference["recipeId"], str)
            or not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", reference["recipeId"])
            or reference["recipeId"] != recipe.get("id")):
        raise ValueError("current recipe capture did not contain an exact reference")
    if RECIPE_SOURCE is not None and reference["sourceId"] != RECIPE_SOURCE:
        raise ValueError("current recipe reference did not match the selected source")
    if RECIPE_ID is not None and reference["recipeId"] != RECIPE_ID:
        raise ValueError("current recipe reference did not match the selected ID")
    outputs = recipe.get("outputs", [])
    if not isinstance(outputs, list) or not any(
            isinstance(output, dict) and isinstance(output.get("stack"), dict)
            and output["stack"].get("itemId") == RECIPE_OUTPUT
            and type(output["stack"].get("count")) is int and output["stack"]["count"] > 0
            for output in outputs):
        raise ValueError("current recipe capture did not contain the selected output")
    return dict(reference)


def recipe_reference(request):
    return captured_recipe_reference(javascript_result(request)["recipe"])


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
        "type": "recipe_grid",
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
            "type": "ingredient_check",
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
            "type": "craftability_summary",
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
            "type": "source_summary",
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


def manual_recipe_arguments():
    """Return an existing closed native recipe value, never a JS-constructed imitation."""
    return {
        "source": recipe_selection_source() + "return recipe;",
        "title": "读取当前原生配方", "description": "返回本次捕获中的真实配方对象，渲染原生配方网格，不修改世界。",
    }


def manual_regression_content(request, completed):
    """Long Chinese test text. Recipe components still use the actual Tool capture."""
    if completed == 0:
        return ("# 原生图形长回复验收\n\n"
                "这是明确标记的固定本地测试端点，不是真实模型。"
                "接下来通过正式只读工具查询当前配方与背包，不修改世界。")
    tools = current_tool_results(request)
    if len(tools) != completed or completed not in (1, 2):
        raise ValueError("manual fixture requires exactly two actual recipe Tools")
    native_recipe = parse_result_preview(tools[0].get("content", ""), require_complete=False)
    native_reference = captured_recipe_reference(native_recipe)
    if completed == 1:
        return ("## 原生图形长回复验收\n\n已返回实际捕获的原生配方。"
                "接下来计算实际背包材料，结果状态由正式工具决定。")
    content = assistant_content(request, completed)
    result = javascript_result(request)
    if recipe_reference(request) != native_reference:
        raise ValueError("recipe analysis did not use the first native recipe reference")
    outputs = result["recipe"].get("outputs", [])
    items = [{"itemId": output["stack"]["itemId"],
              "count": output["stack"]["count"], "label": RECIPE_LABEL}
             for output in outputs if isinstance(output, dict)
             and isinstance(output.get("stack"), dict)]
    if items:
        component = {"type": "item_row", "properties": {"items": items},
                     "fallback": "物品来自本次捕获的配方输出", "narration": "显示实际配方输出物品"}
        content += "\n\n```openallay-component\n" + json.dumps(component, ensure_ascii=False, separators=(",", ":")) + "\n```\n"
    steps = ["## 原生图形长回复验收\n\n" + content,
             "\n\n## 全文滚动检查\n\n"]
    for index in range(1, 49):
        steps.append(f"{index}. 本地验收阅读段 {index:02d}：请检查中文行距、正文对比、卡片边界和滚轮。"
                     "本段是固定测试文案；配方和材料仍来自上方的实际捕获，不声称背包存在未读取的物品。\n")
    steps.append("\n**全文末尾：原生图形长回复验收完成。**\n")
    return "".join(steps)


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


def ui_stop_arguments():
    """Cancellable Rhino work; no Java, I/O, commands, or native world writes."""
    return {"source": "var observed=mc.player.uuid; while(true){} return observed;",
            "title": "读取状态并等待明确停止",
            "description": "读取脱离游戏对象的玩家状态，然后等待真实请求取消；不会宣称有返回结果或修改世界。"}


def ui_provider_failure_arguments():
    return {"source": "return {player:mc.player};",
            "title": "读取当前玩家状态", "description": "读取实际捕获的玩家状态；随后由明确标记的 loopback 验收端点返回受控传输失败。"}


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
    intents = {
        "acceptance": ("建造与读取在线验收站点", "调用六个小型预设、几何、地形路径和模板变换，并保留失败、取消及撤销结果供独立原生检查。"),
        "disabled": ("检查未授权时的 Builder 访问", "尝试打开实际在线 Builder；未启用的 Java 权限应返回工具失败，不宣称世界写入成功。"),
        "reload": ("读取保留的原生站点", "按先前原生记录的坐标读取现存方块、模板及操作日志，不移动玩家或重放写入。"),
        "partial": ("保留部分写入的失败结果", "先写入一个标记，再请求无效方块，读取实际部分失败状态。"),
        "cancel": ("检查明确取消后的写入", "写入标记后取消该在线会话，并检查后续写入是否被拒绝。"),
        "undo": ("检查撤销与冲突", "保留一次外部改动并明确撤销先前操作，读取还原数与冲突。"),
        "server-denied": ("检查服务端模型的 Java 隔离", "尝试实际 Java 桥访问并保留结构化权限失败，不获取额外权限。"),
    }
    title, description = intents[scenario]
    return {"source": source, "title": title, "description": description}


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
        manual_regressions = user_text.startswith("OpenAllay E2E UI manual regressions")
        ui_provider_failure = user_text.startswith("OpenAllay E2E UI provider failure")
        ui_stop = user_text.startswith("OpenAllay E2E UI stop")
        if ui_stop and completed >= 1:
            self.send_error(422, "The actual Stop fixture unexpectedly continued after a Tool result")
            return
        if ui_provider_failure and completed >= 1:
            # Actual controlled loopback transport failure after a real read-only JS result.
            self.send_error(503, "Deterministic E2E continuation transport failure")
            return
        history_seed = user_text.startswith("OpenAllay E2E 历史分页种子 ")
        server_client_tools = user_text.startswith(
            "OpenAllay E2E 服务端模型反向工具验收")
        game_state = (user_text.startswith("OpenAllay E2E 游戏外层状态验收")
                      or server_client_tools)
        world_query_permission_denied = False
        try:
            builder = builder_scenario(user_text)
            builder_step, builder_content = builder_turn(builder, turn_messages, user_text) if builder else (None, None)
            if ui_provider_failure or ui_stop:
                builder_step = (JAVASCRIPT_TOOL, ui_stop_arguments() if ui_stop else ui_provider_failure_arguments())
                builder_content = ("# Deterministic UI Stop fixture\n\nStarting real cancellable read-only Rhino work for an explicit Stop action. No result is pre-authored."
                                   if ui_stop else "# Deterministic UI transport fixture\n\nReading actual detached player state before a controlled loopback continuation failure. Not a live model.")
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
                builder_content if ui_provider_failure or ui_stop
                else (builder_content or "# Deterministic Builder real-client fixture\n\nPre-authored loopback provider. Calling the real bundled Extension through the production Tool; no live-model claim.") if builder
                else "历史分页种子已记录。" if history_seed
                else "服务端模型已完成客户端状态读取；无权限的只读世界查询作为工具失败返回后，Agent 仍正常完成。"
                if server_client_tools and completed == len(GAME_STATE_STEPS)
                else game_state_assistant_content(
                    completed, world_query_permission_denied) if game_state
                else manual_regression_content(request, completed) if manual_regressions
                else assistant_content(request, completed))
        except ValueError as failure:
            self.send_error(422, str(failure))
            return
        deltas = content_events(content)
        steps = GAME_STATE_STEPS if game_state else (JAVASCRIPT_TOOL,) * (2 if manual_regressions else 1)
        if builder_step is not None or (not builder and not history_seed and completed < len(steps)):
            try:
                name, arguments = (builder_step if builder or ui_provider_failure or ui_stop
                                   else steps[completed] if game_state
                                   else (JAVASCRIPT_TOOL, manual_recipe_arguments() if manual_regressions and completed == 0
                                         else javascript_arguments()))
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
