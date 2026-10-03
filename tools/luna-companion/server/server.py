"""Dependency-free, read-only MCP stdio adapter for Luna's local inspection bridge."""
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import subprocess

sys.path.insert(0, str(Path(__file__).resolve().parent))
from harness import Harness

HARNESS = Harness()

VERSION = "0.2.0"
SUPPORTED_PROTOCOLS = ("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25")


def tool(name, description, properties=None, required=None):
    return {
        "name": name,
        "description": description,
        "inputSchema": {"type": "object", "properties": properties or {},
                        "required": required or [], "additionalProperties": False},
        "annotations": {"readOnlyHint": True, "destructiveHint": False,
                        "idempotentHint": True, "openWorldHint": False},
    }


TOOLS = [
    tool("luna_status", "Inspect current game tick, service state, and online human/bot counts."),
    tool("luna_online_players", "List online usernames and whether each player is a bot."),
    tool("luna_inspect_trade", "Inspect both offers in a player's current trade. Reports offer/confirm stage, item IDs and quantities. Does not report prices or bot decision reasons.",
         {"username": {"type": "string", "minLength": 1, "maxLength": 12}}, ["username"]),
]

TOOLS += [
    tool("luna_diagnostics", "JVM memory, GC, thread counts and deadlocks; works even if the game thread is blocked."),
    tool("luna_threads", "Bounded JVM thread stacks and states; no local variable values."),
    tool("luna_events", "Recent bounded game events, including slow ticks and tick errors."),
    tool("luna_collectors", "List registered read-only game-thread snapshot collectors."),
    tool("luna_collect", "Read a registered game-state collector.", {"collector": {"type": "string"}}, ["collector"]),
    tool("luna_harness", "Show session-owned processes, job results and lifecycle events."),
    tool("luna_build", "Build the dedicated clone's runnable distribution (asynchronous)."),
    tool("luna_run_tests", "Run Java tests in the dedicated clone (asynchronous).", {"filter": {"type": "string"}}),
    tool("luna_start", "Start the built Luna runtime in the dedicated clone; requires a successful luna_build."),
    tool("luna_job", "Read a job's exit code and test-suite results.", {"job": {"type": "string"}}, ["job"]),
    tool("luna_logs", "Read bounded, redacted job output with a sequence cursor.",
         {"job": {"type": "string"}, "after": {"type": "integer", "minimum": 0},
          "limit": {"type": "integer", "minimum": 1, "maximum": 200}}, ["job"]),
    tool("luna_stop", "Force-stop only a job owned by this MCP session; may interrupt pending runtime saves.",
         {"job": {"type": "string"}}, ["job"]),
]
for definition in TOOLS:
    if definition["name"] in {"luna_build", "luna_run_tests", "luna_start", "luna_stop"}:
        definition["annotations"].update(readOnlyHint=False, idempotentHint=False,
                                          destructiveHint=definition["name"] == "luna_stop")


def result(data):
    return {"content": [{"type": "text", "text": json.dumps(data)}],
            "structuredContent": data, "isError": "error" in data}


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def bridge(path):
    token = os.getenv("LUNA_COMPANION_TOKEN")
    if token is None:
        token_path = Path.home() / ".luna-companion" / "token"
        token = token_path.read_text(encoding="utf-8").strip() if token_path.is_file() else ""
    if len(token) < 32 or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-" for c in token):
        raise ValueError("Set LUNA_COMPANION_TOKEN to the same URL-safe token used by Luna (at least 32 characters).")
    port = int(os.getenv("LUNA_COMPANION_PORT", "8787"))
    if not 1024 <= port <= 65535:
        raise ValueError("LUNA_COMPANION_PORT must be between 1024 and 65535.")
    request = urllib.request.Request(
        f"http://127.0.0.1:{port}{path}",
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"})
    # Ignore system proxies and never forward the token through redirects.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirects())
    with opener.open(request, timeout=5) as response:
        raw = response.read(2_000_001)
        if len(raw) > 2_000_000:
            raise ValueError("Luna snapshot exceeded the response limit.")
        return json.loads(raw)


def call_tool(params):
    if not isinstance(params, dict):
        raise ValueError("Tool parameters must be an object.")
    name = params.get("name")
    args = params.get("arguments", {})
    if not isinstance(args, dict):
        raise ValueError("Tool arguments must be an object.")
    schema = next((t["inputSchema"] for t in TOOLS if t["name"] == name), None)
    if schema is None or set(args) - set(schema["properties"]) or set(schema["required"]) - set(args):
        raise ValueError("Unknown tool or unexpected/missing arguments.")
    for key, value in args.items():
        expected = schema["properties"][key]["type"]
        if (expected == "string" and not isinstance(value, str)) or (expected == "integer" and type(value) is not int):
            raise ValueError("Invalid argument type.")
    try:
        if name == "luna_harness":
            return result(HARNESS.status())
        if name in ("luna_build", "luna_run_tests", "luna_start"):
            return result(HARNESS.start({"luna_build": "build", "luna_run_tests": "tests", "luna_start": "runtime"}[name], args.get("filter")))
        if name == "luna_job":
            return result(HARNESS.job(args["job"]))
        if name == "luna_logs":
            return result(HARNESS.logs(args["job"], args.get("after", 0), args.get("limit", 100)))
        if name == "luna_stop":
            return result(HARNESS.stop(args["job"]))
    except (OSError, subprocess.SubprocessError):
        return failure("Harness operation failed. Check local configuration and process permissions.")
    if name in ("luna_status", "luna_online_players"):
        if args:
            raise ValueError("This tool does not accept arguments.")
        path = "/status" if name == "luna_status" else "/players"
    elif name == "luna_inspect_trade":
        username = args.get("username")
        if set(args) != {"username"} or not isinstance(username, str) or not username.strip() or len(username) > 12:
            raise ValueError("Supply a username of 1–12 characters.")
        path = "/trade?" + urllib.parse.urlencode({"username": username})
    elif name in ("luna_diagnostics", "luna_threads", "luna_collectors", "luna_events"):
        path = {"luna_diagnostics": "/diagnostics", "luna_threads": "/threads", "luna_collectors": "/collectors", "luna_events": "/events"}[name]
    elif name == "luna_collect":
        import re
        if not re.fullmatch(r"[a-z][a-z0-9_-]{0,47}", args["collector"]):
            raise ValueError("Invalid collector name.")
        path = "/collect/" + args["collector"]
    else:
        raise ValueError("Unknown Luna tool.")
    try:
        data = bridge(path)
        return {"content": [{"type": "text", "text": json.dumps(data)}],
                "structuredContent": data, "isError": "error" in data}
    except urllib.error.HTTPError as error:
        return failure(f"Luna bridge returned HTTP {error.code}. Check the token and server setup.")
    except (OSError, ValueError) as error:
        # Avoid printing request headers, environment values, or raw server errors.
        message = str(error) if isinstance(error, ValueError) else "Cannot reach Luna's local bridge. Start Luna with the bridge enabled."
        return failure(message)


def failure(message):
    return {"content": [{"type": "text", "text": message}], "isError": True}


def dispatch(request):
    if not isinstance(request, dict) or request.get("jsonrpc") != "2.0":
        return {"jsonrpc": "2.0", "id": None, "error": {"code": -32600, "message": "Invalid request"}}
    if "id" not in request:
        return None
    response = {"jsonrpc": "2.0", "id": request["id"]}
    method = request.get("method")
    params = request.get("params", {})
    try:
        if method == "initialize":
            if not isinstance(params, dict):
                raise ValueError("Initialize parameters must be an object.")
            requested = params.get("protocolVersion")
            response["result"] = {
                "protocolVersion": requested if requested in SUPPORTED_PROTOCOLS else SUPPORTED_PROTOCOLS[-1],
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "luna-companion", "version": VERSION},
            }
        elif method == "ping":
            response["result"] = {}
        elif method == "tools/list":
            response["result"] = {"tools": TOOLS}
        elif method == "tools/call":
            response["result"] = call_tool(params)
        else:
            response["error"] = {"code": -32601, "message": "Method not found"}
    except ValueError as error:
        response["error"] = {"code": -32602, "message": str(error)}
    return response


def main():
    for line in sys.stdin:
        try:
            request = json.loads(line)
            response = dispatch(request)
        except (json.JSONDecodeError, UnicodeError):
            response = {"jsonrpc": "2.0", "id": None,
                        "error": {"code": -32700, "message": "Parse error"}}
        if response is not None:
            print(json.dumps(response, ensure_ascii=True), flush=True)


if __name__ == "__main__":
    try:
        main()
    finally:
        HARNESS.close()
