#!/usr/bin/env python3
"""Deterministic loopback-only OpenAI-compatible fixture for real-client E2E."""

import argparse
import hashlib
import json
import os
import time
import threading
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


LIVE_UX_PREFIX = "OpenAllay E2E UI live UX regressions"
LIVE_UX_FOLLOW_UP = "OpenAllay E2E UI live UX follow-up"
LIVE_UX_STEER = "OpenAllay E2E UI live UX steer"
# Controls fixture transport only. It cannot insert a service receipt or native card.
LIVE_UX_RELEASE = threading.Event()


def user_message_text(message):
    """Route current OpenAI text parts without changing visual input or history."""
    content = message.get("content")
    if isinstance(content, str):
        return content
    if not isinstance(content, list):
        raise ValueError("user content must be text or current OpenAI content parts")
    texts = []
    for part in content:
        if not isinstance(part, dict):
            raise ValueError("user content part must be an object")
        if part.get("type") == "text":
            if set(part) != {"type", "text"} or not isinstance(part["text"], str):
                raise ValueError("user text part is malformed")
            texts.append(part["text"])
        elif part.get("type") == "image_url":
            image = part.get("image_url")
            if (set(part) != {"type", "image_url"} or not isinstance(image, dict)
                    or set(image) != {"url"} or not isinstance(image["url"], str)
                    or not image["url"]):
                raise ValueError("user image_url part is malformed")
            # The request retains the full part. Never stringify an image as player text.
        else:
            raise ValueError("unsupported current OpenAI user content part")
    return "\n".join(texts)


def live_ux_turn(request):
    """Keep actual Tool chronology when a real in-flight Steer appends a user message."""
    messages = request.get("messages", [])
    latest = next((index for index in range(len(messages) - 1, -1, -1)
                   if messages[index].get("role") == "user"), -1)
    if latest < 0:
        return None
    latest_text = user_message_text(messages[latest])
    if not latest_text.startswith(LIVE_UX_STEER):
        return None
    root = next((index for index in range(latest - 1, -1, -1)
                 if messages[index].get("role") == "user"
                 and user_message_text(messages[index]).startswith(LIVE_UX_PREFIX)), -1)
    if root < 0:
        raise ValueError("live UX Steer has no actual original request")
    return user_message_text(messages[root]), messages[root + 1:]


def live_ux_analysis_arguments():
    arguments = javascript_arguments()
    # A genuine title-only second tool exercises the 28px compact row; first native recipe remains 40px.
    arguments["description"] = ""
    return arguments


def live_ux_content(request, completed):
    content = manual_regression_content(request, completed)
    return content.replace("原生图形长回复验收", "原生实时 UX 验收").replace(
        "全文末尾：原生实时 UX 验收完成。", "全文末尾：原生实时 UX 验收完成 · LATEST-48。")


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
BUILDER_SCENARIOS = ("restricted", "acceptance", "reload", "partial", "cancel", "undo", "server-denied", "legacy-shapes")
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


BUILDER_OPERATION_NAMES = ("house", "skyscraper", "cottage", "windmill", "farm", "dock",
                           "geometry_decoration", "terrain", "templates")
BUILDER_SKYSCRAPER_SKIP = {'name': 'skyscraper', 'status': 'SKIPPED', 'reason': 'missing_material_palette_role', 'role': 'lightning_rod_up'}


BUILDER_FAILURE_CODES = {"partial": "invalid_native_input", "cancel": "session_closed"}
BUILDER_FAILURE_MESSAGES = {"partial": "Builder native operation failed; inspect the session status",
                            "cancel": "Builder session is closed or cancelled"}


def builder_require(condition, message):
    if not condition:
        raise ValueError(message)


def builder_skipped(receipt):
    skipped = receipt.get("skipped")
    builder_require(isinstance(skipped, list) and (skipped == [] or skipped == [BUILDER_SKYSCRAPER_SKIP]),
                    "Builder skipped cases differ from the exact skyscraper palette-role receipt")
    return skipped


def builder_operation_names(receipt):
    return tuple(name for name in BUILDER_OPERATION_NAMES
                 if not builder_skipped(receipt) or name != "skyscraper")


def builder_journals(rows):
    builder_require(isinstance(rows, list), "Builder durable operations must be an exact list")
    result = {}
    for row in rows:
        builder_require(isinstance(row, dict) and set(row) == {"id", "label", "status", "entries"},
                        "Builder durable operation row has a non-public shape")
        builder_require(all(isinstance(row[key], str) and row[key] for key in ("id", "label", "status"))
                        and row["status"] in ("completed", "failed", "cancelled", "interrupted", "running")
                        and type(row["entries"]) is int and row["entries"] >= 0,
                        "Builder durable operation fields are invalid")
        builder_require(row["id"] not in result, "Builder durable operation ID is duplicated")
        result[row["id"]] = row
    return result


def builder_context(value):
    builder_require(isinstance(value, dict) and set(value) == {"dimension", "playerUuid"}
                    and all(isinstance(item, str) and item for item in value.values()),
                    "Builder context did not retain dimension and exact player")
    return value


def builder_anchor(value):
    builder_require(isinstance(value, dict) and set(value) == {"x", "y", "z"}
                    and all(type(item) is int and -2147483648 <= item <= 2147483647 for item in value.values()),
                    "Builder anchor is not exact native coordinates")
    return value


def builder_positions(anchor, kind, composite):
    x, z = (44, {"partial": 32, "cancel": 34, "undo": 36}[kind]) if composite else (0, 0)
    return [{"x": anchor["x"] + x + dx, "y": anchor["y"] + 1, "z": anchor["z"] + z} for dx in (0, 1)]


def builder_images(images, ids):
    builder_require(isinstance(images, list) and len(images) == len(ids), "Builder native image count differs")
    for image, expected in zip(images, ids):
        builder_require(isinstance(image, dict) and image.get("id") == expected
                        and isinstance(image.get("properties"), dict)
                        and all(isinstance(key, str) and isinstance(value, str)
                                for key, value in image["properties"].items()),
                        "Builder native image differs from the controlled marker")
        builder_require(set(image) == {"id", "properties"} and image["properties"] == {},
                        "Builder native marker image contains unexpected properties or block entity")
    return images


def builder_probe_label(token, kind):
    builder_require(isinstance(token, str) and token and len(token) <= 128,
                    "Builder probe token is invalid")
    return "OpenAllay E2E lifecycle " + token + " " + kind


def builder_success(text, scenario, stage):
    value = parse_result_preview(text)
    builder_require(isinstance(value, str), "Builder result must be a complete scalar JSON receipt")
    try:
        receipt = json.loads(value, object_pairs_hook=builder_unique_fields, parse_constant=builder_invalid_constant)
    except (json.JSONDecodeError, TypeError) as failure:
        raise ValueError("Builder scalar receipt is not exact JSON") from failure
    builder_require(isinstance(receipt, dict) and receipt.get("scenario") == "builder_" + scenario
                    and receipt.get("stage") == stage, "Builder result identified the wrong scenario or stage")
    builder_context(receipt.get("context")); builder_anchor(receipt.get("anchor"))
    builder_probe_label(receipt.get("probeToken"), "partial")
    return receipt


def builder_invalid_constant(value):
    raise ValueError("Builder receipt contains a non-JSON numeric constant: " + value)


def builder_unique_fields(pairs):
    result = {}
    for key, value in pairs:
        builder_require(key not in result, "Builder scalar receipt has duplicate JSON fields")
        result[key] = value
    return result


def builder_same_origin(receipt, baseline):
    for key in (("context", "anchor", "probeToken", "skipped")
                if baseline.get("scenario") == "builder_acceptance" else ("context", "anchor", "probeToken")):
        builder_require(receipt.get(key) == baseline.get(key), "Builder observation changed its native " + key)


def builder_completed(status, writes=None):
    builder_require(isinstance(status, dict) and status.get("state") == "completed",
                    "Builder observation did not finish its genuine native session")
    if writes is not None:
        builder_require(type(status.get("writes")) is int and status["writes"] == writes,
                        "Builder observation unexpectedly wrote to the world")


def builder_baseline(receipt, scenario):
    composite = scenario == "acceptance"
    records = builder_journals(receipt.get("baselineOperations"))
    builder_completed(receipt.get("status"), None if composite else 0)
    kinds = ("partial", "cancel", "undo") if composite else (scenario,)
    lifecycle = receipt.get("lifecycle")
    builder_require(isinstance(lifecycle, dict) and set(lifecycle) == set(kinds),
                    "Builder prerequisite lifecycle cases differ")
    for kind in kinds:
        item = lifecycle[kind]
        builder_require(isinstance(item, dict) and set(item) == {"positions", "beforeImages"}
                        and item["positions"] == builder_positions(receipt["anchor"], kind, composite),
                        "Builder prerequisite positions differ")
        builder_images(item["beforeImages"], ["minecraft:air", "minecraft:air"])
    if composite:
        operations = receipt.get("operations")
        names = builder_operation_names(receipt)
        builder_require(isinstance(operations, list) and len(operations) == len(names),
                        "Builder build did not retain the exact non-skipped completed operations")
        ids = set()
        for name, operation in zip(names, operations):
            builder_require(isinstance(operation, dict) and operation.get("name") == name
                            and operation.get("state") == "completed"
                            and isinstance(operation.get("operationId"), str)
                            and operation["operationId"] not in ids,
                            "Builder build operation order, state or ID differs")
            ids.add(operation["operationId"])
            record = records.get(operation["operationId"])
            builder_require(record is not None and record["status"] == "completed" and record["entries"] > 0
                            and record["label"] == "OpenAllay E2E Builder acceptance",
                            "Builder completed operation lacks its exact durable record")
        builder_require(set(records) == ids, "Builder build has extra unlinked durable operations")
        builder_require(receipt["status"].get("operationId") == operations[-1]["operationId"],
                        "Builder completed build status lost its exact last operation")
        actions = receipt.get("actions")
        builder_require(isinstance(actions, list), "Builder build actions are not an exact list")
        if builder_skipped(receipt):
            builder_require(not any(isinstance(item, dict) and item.get("name") == "skyscraper" for item in actions)
                            and "skyscraper" not in receipt.get("sites", {}),
                            "Builder skipped skyscraper retained a build action or site")
        paths = [action for action in actions if isinstance(action, dict)
                 and action.get("name") in ("terrain_path", "terrain_smart_path")]
        builder_require(len(paths) == 2 and {item["name"] for item in paths} == {"terrain_path", "terrain_smart_path"}
                        and all(item.get("status") == "built" for item in paths),
                        "Builder completed build lost its actual built path receipts")
        templates = receipt.get("templates")
        builder_require(isinstance(templates, dict) and templates.get("listed") is True
                        and templates.get("saved") == ["openallay_e2e_builder_native"],
                        "Builder build did not save and list the actual native template")
    else:
        builder_require(not records, "Standalone lifecycle baseline contains earlier operations")
    return records


def builder_observation(receipt, baseline, previous, kind, failure):
    builder_same_origin(receipt, baseline)
    builder_completed(receipt.get("observationStatus"), 0)
    records = builder_journals(receipt.get("durableOperations"))
    builder_require(all(records.get(key) == row for key, row in previous.items()),
                    "Builder observation changed a baseline durable operation")
    new = set(records) - set(previous)
    builder_require(len(new) == 1, "Builder expected failure did not add exactly one new durable operation")
    journal = records[next(iter(new))]
    expected_state = {"partial": "failed", "cancel": "cancelled"}[kind]
    builder_require(journal["label"] == builder_probe_label(baseline["probeToken"], kind)
                    and journal["status"] == expected_state and journal["entries"] == 1,
                    "Builder expected failure lacks its exact terminal durable receipt")
    lifecycle = receipt.get("lifecycle")
    builder_require(isinstance(lifecycle, dict) and set(lifecycle) == {kind},
                    "Builder observation identified the wrong lifecycle case")
    item = lifecycle[kind]
    builder_require(isinstance(item, dict) and set(item) == {"failure", "journal", "positions", "beforeImages", "afterImages"}
                    and item["failure"] == failure and item["journal"] == journal
                    and item["positions"] == baseline["lifecycle"][kind]["positions"]
                    and item["beforeImages"] == baseline["lifecycle"][kind]["beforeImages"],
                    "Builder observation lost actual failure, journal or before-image identity")
    builder_images(item["afterImages"], ["minecraft:gold_block" if kind == "partial" else "minecraft:diamond_block", "minecraft:air"])
    return records, item


def builder_undo_receipt(receipt, baseline, previous):
    builder_same_origin(receipt, baseline)
    records = builder_journals(receipt.get("durableOperations"))
    builder_require(all(records.get(key) == row for key, row in previous.items()),
                    "Builder undo changed an earlier durable operation")
    lifecycle = receipt.get("lifecycle")
    builder_require(isinstance(lifecycle, dict) and set(lifecycle) == {"undo"},
                    "Builder undo stage identified the wrong lifecycle case")
    item = lifecycle["undo"]
    builder_require(isinstance(item, dict) and set(item) == {"result", "status", "originalStatus", "interventionStatus", "positions", "beforeImages", "afterImages"},
                    "Builder undo receipt has a wrong shape")
    before = baseline["lifecycle"]["undo"]
    builder_require(item["positions"] == before["positions"] and item["beforeImages"] == before["beforeImages"],
                    "Builder undo before-images or positions differ")
    added = {}
    for key, entries, label in (("originalStatus", 2, "undo original"), ("interventionStatus", 1, "undo intervention"), ("status", 1, None)):
        status = item[key]; builder_completed(status)
        id_ = status.get("operationId")
        row = records.get(id_)
        builder_require(row is not None and id_ not in previous and id_ not in added
                        and row["status"] == "completed" and row["entries"] == entries,
                        "Builder undo operation lacks exact new completed journal")
        expected_label = "Undo " + item["originalStatus"].get("operationId", "") if label is None else builder_probe_label(baseline["probeToken"], label)
        builder_require(row["label"] == expected_label, "Builder undo journal label differs")
        added[id_] = row
    builder_require(set(records) - set(previous) == set(added), "Builder undo added unlinked durable operations")
    result = item["result"]
    builder_require(isinstance(result, dict) and result.get("restored") == 1 and type(result["restored"]) is int
                    and result.get("conflicts") == [before["positions"][1]] and result.get("uncertain") == []
                    and result.get("operationId") == item["status"].get("operationId"),
                    "Builder undo did not retain exact restore and conflicting cell")
    builder_images(item["afterImages"], ["minecraft:air", "minecraft:diamond_block"])
    return records, item


def builder_expected_failure(text, kind):
    failure = parse_tool_failure(text)
    builder_require(failure is not None and failure["code"] == BUILDER_FAILURE_CODES[kind]
                    and failure["message"] == BUILDER_FAILURE_MESSAGES[kind],
                    "Builder " + kind + " probe did not return its exact expected native failure")
    return failure


def builder_source(source, title, description):
    return {"source": source, "title": title, "description": description}


def builder_native_open(label):
    return ('var building=require("openallay_builder:building");\n'
            'var b=building.open({seed:17,label:' + json.dumps(label) + '});\n')


def builder_stage_header(baseline, label, full=False):
    if not full:
        keys = ("context", "anchor", "probeToken", "lifecycle", "baselineOperations")
        if baseline.get("scenario") == "builder_acceptance":
            keys += ("skipped",)
        baseline = {key: baseline[key] for key in keys}
    return (builder_native_open(label) + 'var expected=' + json.dumps(baseline, separators=(",", ":")) + ';\n'
            'var c=b.context(),context={dimension:c.dimension,playerUuid:c.player.uuid};\n'
            'if(context.dimension!==expected.context.dimension || context.playerUuid!==expected.context.playerUuid) '
            'throw new Error("Builder active world context changed");\n')


def builder_failure_arguments(baseline, kind):
    label = builder_probe_label(baseline["probeToken"], kind)
    source = builder_stage_header(baseline, label)
    source += 'var positions=expected.lifecycle.' + kind + '.positions;\n'
    source += 'var images=b.get_blocks(positions).map(function(cell){return cell.state;}); if(JSON.stringify(images)!==JSON.stringify(expected.lifecycle.' + kind + '.beforeImages)) throw new Error("Lifecycle before-images changed");\n'
    source += 'var p=positions[0],q=positions[1]; b.place_block(p.x,p.y,p.z,' + json.dumps("gold_block" if kind == "partial" else "diamond_block") + ');\n'
    if kind == "cancel":
        source += 'b.cancel();\n'
    source += 'b.place_block(q.x,q.y,q.z,' + json.dumps("openallay_e2e:missing_native_block" if kind == "partial" else "gold_block") + ');\n'
    source += 'throw new Error("Expected native rejection unexpectedly returned");'
    return builder_source(source, "检查真实 Builder 终止失败", "调用真实原生操作并保留其失败；后续独立调用读取实际日志和方块。")


def builder_observation_arguments(scenario, baseline, kind, failure):
    stage = kind + "_observation" if scenario == "acceptance" else "final"
    source = builder_stage_header(baseline, "OpenAllay E2E lifecycle observation")
    source += 'var positions=expected.lifecycle.' + kind + '.positions;\n'
    source += 'var receipt={scenario:' + json.dumps("builder_" + scenario) + ',stage:' + json.dumps(stage) + ',probeToken:expected.probeToken,anchor:expected.anchor,context:context,lifecycle:{},durableOperations:b.list_operations()};\n'
    source += 'var matches=receipt.durableOperations.filter(function(row){return row.label===' + json.dumps(builder_probe_label(baseline["probeToken"], kind)) + ';}); if(matches.length!==1) throw new Error("Lifecycle journal is not unique");\n'
    source += 'receipt.lifecycle.' + kind + '={failure:' + json.dumps(failure, separators=(",", ":")) + ',journal:matches[0],positions:positions,beforeImages:expected.lifecycle.' + kind + '.beforeImages,afterImages:b.get_blocks(positions).map(function(cell){return cell.state;})};\n'
    if scenario == "acceptance":
        source += 'receipt.skipped=expected.skipped;'
    source += 'receipt.observationStatus=b.finish();'
    if scenario != "acceptance":
        source += 'receipt.status=receipt.observationStatus;receipt.baselineOperations=expected.baselineOperations;'
    source += 'return JSON.stringify(receipt);'
    return builder_source(source, "读取实际 Builder 终止日志", "在新的在线会话读取唯一持久日志与实际方块，不重放写入或恢复已关闭会话。")


def builder_undo_arguments(baseline):
    source = builder_stage_header(baseline, builder_probe_label(baseline["probeToken"], "undo original"))
    source += 'var positions=expected.lifecycle.undo.positions,before=b.get_blocks(positions).map(function(cell){return cell.state;}); if(JSON.stringify(before)!==JSON.stringify(expected.lifecycle.undo.beforeImages)) throw new Error("Undo before-images changed");\n'
    source += 'for(var i=0;i<positions.length;i++){var p=positions[i];b.place_block(p.x,p.y,p.z,"gold_block");} var original=b.finish();\n'
    source += 'var intervention=building.open({seed:17,label:' + json.dumps(builder_probe_label(baseline["probeToken"], "undo intervention")) + '}); var q=positions[1];intervention.place_block(q.x,q.y,q.z,"diamond_block"); var intervened=intervention.finish();\n'
    source += 'var result=b.undo(original.operationId),status=b.finish();\n'
    source += 'return JSON.stringify({scenario:"builder_acceptance",stage:"undo",skipped:expected.skipped,probeToken:expected.probeToken,anchor:expected.anchor,context:context,durableOperations:b.list_operations(),lifecycle:{undo:{result:result,status:status,originalStatus:original,interventionStatus:intervened,positions:positions,beforeImages:before,afterImages:b.get_blocks(positions).map(function(cell){return cell.state;})}}});'
    return builder_source(source, "检查真实撤销与冲突", "明确撤销实际操作 ID，保留外部改动，并读取真实还原数、冲突位置和方块。")


def builder_final_arguments(baseline, lifecycle):
    combined = dict(baseline); combined.update(stage="final", lifecycle=lifecycle)
    source = builder_stage_header(combined, "OpenAllay E2E final read-only observation", full=True)
    source += 'expected.context=context;expected.durableOperations=b.list_operations();\n'
    source += 'for(var kind in expected.lifecycle) if(Object.prototype.hasOwnProperty.call(expected.lifecycle,kind)) expected.lifecycle[kind].afterImages=b.get_blocks(expected.lifecycle[kind].positions).map(function(cell){return cell.state;});\n'
    source += 'expected.observationStatus=b.finish();return JSON.stringify(expected);'
    return builder_source(source, "复核完整原生验收记录", "重新读取持久日志和生命周期方块，保留先前成功与失败 Tool 的真实结果。")


def builder_baseline_arguments(scenario, token):
    source = builder_native_open("OpenAllay E2E lifecycle baseline")
    source += 'var c=b.context(),p=c.player,anchor={x:Math.floor(p.x)+8,y:Math.floor(p.y)-1,z:Math.floor(p.z)+8};\n'
    source += 'var positions=[{x:anchor.x,y:anchor.y+1,z:anchor.z},{x:anchor.x+1,y:anchor.y+1,z:anchor.z}]; var images=b.get_blocks(positions).map(function(cell){return cell.state;});\n'
    source += 'for(var i=0;i<images.length;i++) if(images[i].id!=="minecraft:air") throw new Error("Lifecycle prerequisite is not air");\n'
    source += 'return JSON.stringify({scenario:' + json.dumps("builder_" + scenario) + ',stage:"baseline",probeToken:' + json.dumps(token) + ',anchor:anchor,context:{dimension:c.dimension,playerUuid:p.uuid},baselineOperations:b.list_operations(),status:b.finish(),lifecycle:{' + scenario + ':{positions:positions,beforeImages:images}}});'
    return builder_source(source, "记录实际生命周期前置状态", "读取受限 Builder 的实际坐标、空气前置状态和日志基线，不执行写入。")


def builder_multistage(scenario, results, token):
    if len(results) == 1:
        if scenario == "acceptance":
            arguments = builder_arguments(scenario)
            arguments["source"] = 'var fixtureProbeToken=' + json.dumps(token) + ';\n' + arguments["source"]
            return (JAVASCRIPT_TOOL, arguments), None
        return (JAVASCRIPT_TOOL, builder_baseline_arguments(scenario, token)), None
    baseline = builder_success(results[1].get("content", ""), scenario, "build" if scenario == "acceptance" else "baseline")
    builder_require(baseline["probeToken"] == token, "Builder returned a different invocation probe token")
    previous = builder_baseline(baseline, scenario)
    kinds = ("partial", "cancel") if scenario == "acceptance" else (scenario,)
    lifecycle = {}
    index = 2
    for kind in kinds:
        if len(results) == index:
            return (JAVASCRIPT_TOOL, builder_failure_arguments(baseline, kind)), None
        failure = builder_expected_failure(results[index].get("content", ""), kind)
        if len(results) == index + 1:
            return (JAVASCRIPT_TOOL, builder_observation_arguments(scenario, baseline, kind, failure)), None
        stage = kind + "_observation" if scenario == "acceptance" else "final"
        observation = builder_success(results[index + 1].get("content", ""), scenario, stage)
        previous, lifecycle[kind] = builder_observation(observation, baseline, previous, kind, failure)
        index += 2
    if scenario == "acceptance":
        if len(results) == index:
            return (JAVASCRIPT_TOOL, builder_undo_arguments(baseline)), None
        undo = builder_success(results[index].get("content", ""), scenario, "undo")
        previous, lifecycle["undo"] = builder_undo_receipt(undo, baseline, previous)
        index += 1
        if len(results) == index:
            return (JAVASCRIPT_TOOL, builder_final_arguments(baseline, lifecycle)), None
        final = builder_success(results[index].get("content", ""), scenario, "final")
        builder_same_origin(final, baseline); builder_completed(final.get("observationStatus"), 0)
        builder_require(builder_journals(final.get("durableOperations")) == previous,
                        "Builder final observation changed exact durable operation receipts")
        for key in ("operations", "actions", "templates", "status", "baselineOperations", "terrain", "sites", "seed", "provider", "skipped"):
            builder_require(final.get(key) == baseline.get(key), "Builder final receipt changed completed build " + key)
        builder_require(final.get("lifecycle") == lifecycle, "Builder final observation changed prior lifecycle outcomes")
        index += 1
    else:
        builder_completed(observation.get("status"), 0)
        builder_require(observation.get("baselineOperations") == baseline["baselineOperations"],
                        "Builder final observation changed the baseline")
    builder_require(len(results) == index, "Builder fixture has unexpected extra current Tool results")
    return None, ("# Deterministic Builder real-client fixture\n\nActual completed build/readback receipts and exact expected failed Tools are retained separately. "
                  "Fresh native observations retain their durable terminal journals and world effects. "
                  "The independent controller readback determines acceptance. This loopback response is pre-authored test content, not a live model.")


def builder_arguments(scenario, retained_anchor=None):
    """Actual Extension/native programs, not fixture backends or claimed geometry."""
    if scenario == "acceptance":
        from pathlib import Path
        source = Path(__file__).with_name("e2e-builder-fixture.js").read_text(encoding="utf-8")
    elif scenario == "legacy-shapes":
        from pathlib import Path
        source = Path(__file__).with_name("e2e-builder-legacy-shapes.js").read_text(encoding="utf-8")
    elif scenario == "server-denied":
        source = ('var System = Java.type("java.lang.System");\n'
                  'return {unexpectedJavaAuthority:true,version:String(System.getProperty("java.version"))};')
    else:
        source = '''var building = require("openallay_builder:building");
var b = building.open({seed:17,label:"OpenAllay E2E SCENARIO"});
var p = b.get_player_pos();
var x=Math.floor(p.x)+8,y=Math.floor(p.y)-1,z=Math.floor(p.z)+8;
'''.replace("SCENARIO", scenario)
        if scenario == "restricted":
            source += '''b.place_block(x,y+1,z,"gold_block");
return JSON.stringify({scenario:"builder_restricted",status:b.finish(),readback:b.get_block(x,y+1,z)});
'''
        elif scenario in ("partial", "cancel"):
            return builder_baseline_arguments(scenario, "unit-only-probe")
        elif scenario == "undo":
            source += '''b.place_block(x,y+1,z,"gold_block");
b.place_block(x+1,y+1,z,"gold_block");
var original=b.finish();
var changed=building.open({seed:17,label:"OpenAllay E2E intervening edit"});
changed.place_block(x+1,y+1,z,"diamond_block");
changed.finish();
var undo=b.undo(original.operationId);
return JSON.stringify({scenario:"builder_undo",undo:undo,status:b.finish()});
'''
        elif scenario == "reload":
            if retained_anchor is None or len(retained_anchor) != 3:
                raise ValueError("reload requires the independently retained native origin")
            source += "x=%d;y=%d;z=%d;\n" % tuple(retained_anchor)
            source += '''var c=b.context(),skipped=[];
if (!c.materialPalette || typeof c.materialPalette!=="object") throw new Error("Builder reload requires the actual native material palette");
if (!Object.prototype.hasOwnProperty.call(c.materialPalette,"lightning_rod_up")) skipped.push({name:"skyscraper",status:"SKIPPED",reason:"missing_material_palette_role",role:"lightning_rod_up"});
var template=b.load_template("openallay_e2e_builder_native");
var listed=b.list_templates();
var operations=b.list_operations();
var readback={house:b.get_block(x,y,z),dock:b.get_block(x+14,y,z+18),
  rotatedStair:b.get_block_full(x+21,y+1,z+32),mirroredChest:b.get_block_full(x+24,y+1,z+33)};
return JSON.stringify({scenario:"builder_reload",skipped:skipped,template:{name:"openallay_e2e_builder_native",size:template.size},
  listed:listed,operations:operations,operationCount:operations.length,readback:readback,status:b.finish()});
'''
    intents = {
        "acceptance": ("建造与读取在线验收站点", "调用具备所需材料的小型预设、几何、地形路径和模板变换，并保留失败、取消及撤销结果供独立原生检查。"),
        "restricted": ("用受限 JavaScript 放置并读取 Builder 标记", "通过已启用的 Builder SDK 放置一个金块，并读取实际方块和会话完成状态。"),
        "reload": ("读取保留的原生站点", "按先前原生记录的坐标读取现存方块、模板及操作日志，不移动玩家或重放写入。"),
        "partial": ("保留部分写入的失败结果", "先写入一个标记，再请求无效方块，读取实际部分失败状态。"),
        "cancel": ("检查明确取消后的写入", "写入标记后取消该在线会话，并检查后续写入是否被拒绝。"),
        "undo": ("检查撤销与冲突", "保留一次外部改动并明确撤销先前操作，读取还原数与冲突。"),
        "server-denied": ("检查服务端模型的 Java 隔离", "尝试实际 Java 桥访问并保留结构化权限失败，不获取额外权限。"),
    }
    intents["legacy-shapes"] = ("Check legacy native geometry and templates", "Build exact native states, retain unavailable preset roles as skipped, and read the saved template and transformed blocks.")
    title, description = intents[scenario]
    return {"source": source, "title": title, "description": description}


def parse_tool_failure(text):
    """Exact current ModelToolTextRenderer failure fields, not normalized JSON."""
    import re
    if not isinstance(text, str) or not text.startswith("status: failure\n"):
        return None
    match = re.fullmatch(r"status: failure\ncode: ([a-z0-9_]+)\nmessage: (.+)", text, re.DOTALL)
    if match is None:
        raise ValueError("Tool failure projection is malformed")
    return {"status": "failure", "code": match[1], "message": match[2]}


def builder_result(text, scenario):
    """Single successful native programs only; staged lifecycle requires complete history."""
    builder_require(scenario in ("restricted", "undo", "reload", "legacy-shapes"),
                    "Builder lifecycle receipt requires its ordered current Tool history")
    value = parse_result_preview(text)
    builder_require(isinstance(value, str), "Builder result must be a complete scalar JSON receipt")
    try:
        receipt = json.loads(value, object_pairs_hook=builder_unique_fields, parse_constant=builder_invalid_constant)
    except json.JSONDecodeError as failure:
        raise ValueError("Builder scalar receipt is not JSON") from failure
    builder_require(isinstance(receipt, dict) and receipt.get("scenario") == "builder_" + scenario.replace("-", "_"),
                    "native Builder result did not identify the requested scenario")
    builder_completed(receipt.get("status"))
    if scenario == "restricted":
        builder_require(receipt.get("readback") == "minecraft:gold_block",
                        "native Builder restricted result did not read back the gold block")
    return receipt


def builder_turn(scenario, turn_messages, user_text="", probe_token="unit-only-probe"):
    """Validate the real current Tool results; never manufacture acceptance."""
    calls = {}
    results = []
    result_ids = set()
    for message in turn_messages:
        if message.get("role") == "assistant":
            for call in message.get("tool_calls", []):
                if (not isinstance(call.get("id"), str) or not call["id"] or call["id"] in calls):
                    raise ValueError("Builder turn contains an invalid or duplicate Tool call ID")
                calls[call.get("id")] = call
        if message.get("role") == "tool":
            if scenario in ("acceptance", "partial", "cancel"):
                result_id = message.get("tool_call_id")
                builder_require(isinstance(result_id, str) and result_id in calls and result_id not in result_ids,
                                "Builder staged result lacks a unique preceding current Tool call")
                result_ids.add(result_id)
            name = message.get("name", calls.get(message.get("tool_call_id"), {}).get("function", {}).get("name"))
            expected = BUILDER_SKILL_TOOL if scenario != "server-denied" and not results else JAVASCRIPT_TOOL
            if name != expected:
                raise ValueError("Builder result is not from the requested current Tool")
            results.append(message)
    if not results:
        if scenario == "server-denied":
            return (JAVASCRIPT_TOOL, builder_arguments(scenario)), None
        return (BUILDER_SKILL_TOOL, {"name": "minecraft-builder"}), None
    if scenario != "server-denied":
        skill_text = results[0].get("content", "")
        header = skill_text.partition("\ncontent:\n")[0] if isinstance(skill_text, str) else ""
        if (not header.startswith("skill_instructions\nskill: minecraft-builder\n")
                or "\nstate: complete\n" not in header + "\n"
                or "\ncomplete: true\n" not in header + "\n"):
            raise ValueError("bundled Builder Skill was not completely loaded")
        if scenario in ("acceptance", "partial", "cancel"):
            builder_require(len(calls) == len(results), "Builder staged turn has unpaired Tool calls")
            if len(results) >= 2:
                builder_require(results[1].get("tool_call_id") == probe_token,
                                "Builder probe token is not the actual first JavaScript call ID")
            # Each result must come from the exact current call generated for its stage.
            for ordinal, message in enumerate(results, 1):
                call = calls.get(message.get("tool_call_id"))
                builder_require(isinstance(call, dict), "Builder staged result lacks its actual current Tool call")
                if ordinal == 1:
                    expected_call = (BUILDER_SKILL_TOOL, {"name": "minecraft-builder"})
                else:
                    expected_call, _ = builder_multistage(scenario, results[:ordinal - 1], probe_token)
                builder_require(expected_call is not None and call.get("function", {}).get("name") == expected_call[0],
                                "Builder staged call name differs")
                arguments = call.get("function", {}).get("arguments")
                if isinstance(arguments, str):
                    try: arguments = json.loads(arguments, object_pairs_hook=builder_unique_fields, parse_constant=builder_invalid_constant)
                    except json.JSONDecodeError as failure: raise ValueError("Builder call arguments are malformed") from failure
                builder_require(arguments == expected_call[1], "Builder staged call arguments differ from the actual requested probe")
            return builder_multistage(scenario, results, probe_token)
        if len(results) == 1:
            anchor = builder_retained_anchor(user_text) if scenario == "reload" else None
            return (JAVASCRIPT_TOOL, builder_arguments(scenario, anchor)), None
    if len(results) != (1 if scenario == "server-denied" else 2):
        raise ValueError("unexpected extra Builder fixture Tool result")
    text = results[-1].get("content", "")
    denied = scenario == "server-denied"
    if not denied:
        failure = parse_tool_failure(text)
        if failure is not None:
            raise ValueError("native Builder fixture failed: " + failure["code"] + ": " + failure["message"])
        builder_result(text, scenario)
        summary = "Native Tool returned the requested " + scenario + " result. The independent controller readback determines acceptance."
    else:
        parsed = None
        try:
            parsed = json.loads(text)
        except (TypeError, json.JSONDecodeError):
            pass
        if isinstance(parsed, dict):
            if parsed.get("status") != "failure" or parsed.get("code") != "javascript_error":
                raise ValueError("server Java isolation did not return actual JavaScript failure")
            message = parsed.get("message")
        else:
            prefix = "status: failure\ncode: javascript_error\nmessage: "
            if not isinstance(text, str) or not text.startswith(prefix):
                raise ValueError("server Java isolation projection lacked exact failure status/code")
            message = text[len(prefix):]
        if not isinstance(message, str) or not message.startswith('ReferenceError: "Java" is not defined.'):
            raise ValueError("server Java isolation did not show unavailable Java authority")
        summary = "Server-model Java access was denied by the real JavaScript Tool. No success is claimed."
    scope = ("It probes the actual JavaScript isolation boundary."
             if denied else "It invokes the actual bundled Extension.")
    return None, ("# Deterministic Builder real-client fixture\n\n" + summary +
                  "\n\nThis loopback response is explicitly pre-authored test content, not a live model. " + scope +
                  " It does not certify its own geometry or visual quality.")


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
            user_text = user_message_text(message)
    steered = live_ux_turn(request)
    return steered if steered is not None else (user_text, messages[latest_user + 1:])


def fixture_tool_call_id(request, tool_ordinal):
    """Stable current-turn identity, unique against retained earlier conversation turns."""
    if tool_ordinal < 1:
        raise ValueError("fixture Tool ordinal must be positive")
    user_text, turn_messages = current_user_turn(request)
    messages = request.get("messages", [])
    # Steer uses the actual original root slice, so appending it does not change this index.
    root_index = len(messages) - len(turn_messages) - 1
    if root_index < 0 or messages[root_index].get("role") != "user" or not isinstance(user_text, str):
        raise ValueError("fixture Tool call requires an actual current user turn")
    turn_digest = hashlib.sha256(user_text.encode("utf-8")).hexdigest()[:16]
    return f"fixture-{root_index}-{turn_digest}-{tool_ordinal}"


class Handler(BaseHTTPRequestHandler):
    server_version = "OpenAllayFixture/1"

    def do_POST(self):
        if self.path == "/__e2e/live-ux/release":
            if self.client_address[0] != "127.0.0.1":
                self.send_error(403, "live UX fixture control is loopback only")
                return
            LIVE_UX_RELEASE.set()
            self.send_response(204)
            self.end_headers()
            return
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return
        length = int(self.headers.get("content-length", "0"))
        try:
            request = json.loads(self.rfile.read(length))
            user_text, turn_messages = current_user_turn(request)
        except (ValueError, TypeError, AttributeError) as failure:
            self.send_error(422, "Malformed fixture request: " + str(failure))
            return
        completed = sum(1 for message in turn_messages
                        if message.get("role") == "tool")
        live_ux = user_text.startswith(LIVE_UX_PREFIX)
        live_ux_follow_up = user_text.startswith(LIVE_UX_FOLLOW_UP)
        manual_regressions = user_text.startswith("OpenAllay E2E UI manual regressions") or live_ux or live_ux_follow_up
        if live_ux and completed == 0:
            LIVE_UX_RELEASE.clear()
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
            builder_step, builder_content = builder_turn(builder, turn_messages, user_text, fixture_tool_call_id(request, 2)) if builder else (None, None)
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
                else live_ux_content(request, completed) if live_ux or live_ux_follow_up
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
                                         else live_ux_analysis_arguments() if live_ux or live_ux_follow_up
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
                "id": fixture_tool_call_id(request, completed + 1),
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
        if live_ux and (" hold" in user_text or " gameplay-toast" in user_text) and completed == 1 and not LIVE_UX_RELEASE.wait(timeout=120):
            self.send_error(504, "live UX native action window was not explicitly released")
            return
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
