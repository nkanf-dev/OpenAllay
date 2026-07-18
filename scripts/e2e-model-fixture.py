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
)


def recipe_reference(request):
    for message in reversed(request.get("messages", [])):
        if message.get("role") != "tool":
            continue
        try:
            normalized = json.loads(message.get("content", ""))
            recipes = normalized["value"]["recipes"]
            reference = recipes[0]["reference"]
            return {key: reference[key]
                    for key in ("sourceId", "generation", "recipeId")}
        except (KeyError, IndexError, TypeError, json.JSONDecodeError):
            continue
    raise ValueError("search result did not contain a recipe reference")


def step(request, completed):
    if completed == 0:
        return STEP_NAMES[completed], {"outputItem": "minecraft:iron_block"}
    if completed == 1:
        return STEP_NAMES[completed], recipe_reference(request)
    if completed == 2:
        return STEP_NAMES[completed], {}
    reference = recipe_reference(request)
    reference["crafts"] = 1
    return STEP_NAMES[completed], reference


class Handler(BaseHTTPRequestHandler):
    server_version = "TomeWispFixture/1"

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return
        length = int(self.headers.get("content-length", "0"))
        request = json.loads(self.rfile.read(length))
        completed = sum(1 for message in request.get("messages", [])
                        if message.get("role") == "tool")
        if completed < len(STEP_NAMES):
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
            delta = {"tool_calls": [{
                "index": 0,
                "id": "fixture-" + str(completed + 1),
                "type": "function",
                "function": {"name": name, "arguments": json.dumps(arguments)},
            }]}
            reason = "tool_calls"
        else:
            delta = {"content": "已完成真实客户端多工具链路；请以工具证据为准。"}
            reason = "stop"
        event = {
            "id": "tomewisp-fixture",
            "model": "tomewisp-e2e-fixture",
            "choices": [{"index": 0, "delta": delta, "finish_reason": reason}],
            "usage": {"prompt_tokens": 10, "completion_tokens": 4},
        }
        body = ("data: " + json.dumps(event, separators=(",", ":"))
                + "\n\ndata: [DONE]\n\n").encode()
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
