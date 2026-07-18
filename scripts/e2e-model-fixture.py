#!/usr/bin/env python3
"""Deterministic loopback-only OpenAI-compatible fixture for real-client E2E."""

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


STEP_NAMES = (
    "tomewisp__search_recipes",
    "tomewisp__get_recipe",
    "tomewisp__inspect_inventory",
    "tomewisp__calculate_craftability",
    "tomewisp__list_knowledge_sources",
)


def recipe_reference(request, output_item=None, recipe_id=None, recipe_type=None):
    matching_search = None
    observed_tools = []
    for message in reversed(request.get("messages", [])):
        if message.get("role") != "tool":
            continue
        try:
            normalized = json.loads(message.get("content", ""))
            observed_tools.append({
                "status": normalized.get("status") if isinstance(normalized, dict) else None,
                "code": normalized.get("code") if isinstance(normalized, dict) else None,
                "outputType": normalized.get("outputType") if isinstance(normalized, dict) else None,
                "valueKeys": sorted(normalized.get("value", {}).keys())
                if isinstance(normalized, dict)
                and isinstance(normalized.get("value"), dict) else [],
            })
            if output_item is not None:
                query = normalized["value"]["query"]
                if query.get("outputItem") != output_item:
                    continue
            matching_search = normalized
            recipes = normalized["value"]["recipes"]
            for recipe in recipes:
                if recipe_id is not None and recipe.get("id") != recipe_id:
                    continue
                if recipe_type is not None and recipe.get("type") != recipe_type:
                    continue
                reference = recipe["reference"]
                return {key: reference[key]
                        for key in ("sourceId", "generation", "recipeId")}
        except (KeyError, IndexError, TypeError, json.JSONDecodeError):
            continue
    if matching_search is not None:
        providers = matching_search.get("value", {}).get("catalog", {}).get("providers", [])
        summary = [{
            "sourceId": provider.get("sourceId"),
            "state": provider.get("state"),
            "recipeCount": provider.get("recipeCount"),
            "diagnostics": [diagnostic.get("code")
                            for diagnostic in provider.get("diagnostics", [])[:3]],
        } for provider in providers]
        candidates = [{"id": recipe.get("id"), "type": recipe.get("type")}
                      for recipe in matching_search.get("value", {}).get("recipes", [])]
        raise ValueError("search returned no matching recipe reference; candidates="
                         + json.dumps(candidates, separators=(",", ":"))
                         + "; providers="
                         + json.dumps(summary, separators=(",", ":")))
    raise ValueError("search result did not contain a recipe reference; observed="
                     + json.dumps(observed_tools, separators=(",", ":")))


def step(request, completed):
    if completed == 0:
        return STEP_NAMES[completed], {"outputItem": "minecraft:iron_block"}
    if completed == 1:
        return STEP_NAMES[completed], recipe_reference(
            request, "minecraft:iron_block", "minecraft:iron_block")
    if completed == 2:
        return STEP_NAMES[completed], {}
    if completed == 3:
        reference = recipe_reference(
            request, "minecraft:iron_block", "minecraft:iron_block")
        reference["crafts"] = 1
        return STEP_NAMES[completed], reference
    return STEP_NAMES[completed], {}


def assistant_content(request, completed):
    if completed == 0:
        return "# Phase 4 图形验收\n\n我会先查询 **铁块配方**，再核对库存和知识来源。"
    if completed == 1:
        return """
已获得搜索证据，现在读取精确配方。

```tomewisp-component
{"schemaVersion":1,"type":"status_badge","properties":{"state":"INFO","label":"配方搜索完成"},"fallback":"配方搜索完成","narration":"配方搜索完成"}
```
""".strip()
    if completed == 2:
        reference = recipe_reference(
            request, "minecraft:iron_block", "minecraft:iron_block")
        component = {
            "schemaVersion": 1,
            "type": "recipe_grid",
            "properties": {
                **reference,
                "label": "铁块配方",
            },
            "fallback": "铁块配方已确认",
            "narration": "铁块配方已确认，可打开配方查看器",
        }
        return "## 精确配方\n\n" + "```tomewisp-component\n" \
            + json.dumps(component, ensure_ascii=False, separators=(",", ":")) \
            + "\n```\n\n下一步检查玩家库存。"
    if completed == 3:
        return """
- 库存已脱离 Minecraft 对象并安全捕获
- 接下来计算一次制作

```tomewisp-component
{"schemaVersion":1,"type":"world_mutation","properties":{"command":"/give"},"fallback":"不支持的组件已安全降级为文本","narration":"不支持的组件"}
```
""".strip()
    if completed == 4:
        return """
> 可制作性由 Java 确定性计算，不交给模型猜测。

```tomewisp-component
{"schemaVersion":1,"type":"progress_steps","properties":{"steps":[{"id":"recipe","label":"配方证据","state":"COMPLETE"},{"id":"inventory","label":"库存证据","state":"COMPLETE"},{"id":"knowledge","label":"知识来源","state":"ACTIVE"}]},"fallback":"配方和库存已检查，正在读取知识来源","narration":"验收进度：配方和库存完成，知识来源进行中"}
```
""".strip()
    return """
## 完成

| 检查项 | 结果 |
|---|---|
| 配方 | 已找到并精确读取 |
| 库存/制作 | 已由工具计算 |
| 知识来源 | 已列出 |

可继续查看 [[tw:item|minecraft:iron_block|铁块]]的配方或用途。
""".strip()


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
    server_version = "TomeWispFixture/1"

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return
        length = int(self.headers.get("content-length", "0"))
        request = json.loads(self.rfile.read(length))
        user_text, turn_messages = current_user_turn(request)
        completed = sum(1 for message in turn_messages
                        if message.get("role") == "tool")
        history_seed = user_text.startswith("TomeWisp E2E 历史分页种子 ")
        deltas = content_events(
            "历史分页种子已记录。" if history_seed
            else assistant_content(request, completed))
        if not history_seed and completed < len(STEP_NAMES):
            try:
                name, arguments = step(request, completed)
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
                "id": "tomewisp-fixture",
                "model": "tomewisp-e2e-fixture",
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
    print(f"TomeWisp E2E model fixture listening on 127.0.0.1:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
