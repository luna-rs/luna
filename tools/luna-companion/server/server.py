"""Dependency-free, read-only MCP stdio adapter for Luna's local inspection bridge."""
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request

VERSION = "0.1.0"
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
    if name in ("luna_status", "luna_online_players"):
        if args:
            raise ValueError("This tool does not accept arguments.")
        path = "/status" if name == "luna_status" else "/players"
    elif name == "luna_inspect_trade":
        username = args.get("username")
        if set(args) != {"username"} or not isinstance(username, str) or not username.strip() or len(username) > 12:
            raise ValueError("Supply a username of 1–12 characters.")
        path = "/trade?" + urllib.parse.urlencode({"username": username})
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
    main()
