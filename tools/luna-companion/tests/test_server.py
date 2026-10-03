import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from unittest.mock import patch

ENTRY = Path(__file__).resolve().parents[1] / "server" / "server.py"
SPEC = importlib.util.spec_from_file_location("luna_mcp", ENTRY)
MCP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MCP)
TOKEN = "a" * 48


class FixtureHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.headers.get("Authorization") != "Bearer " + TOKEN:
            self.send_response(401)
            self.end_headers()
            return
        data = {"/status": {"tick": 12, "online": 2, "bots": 1, "humans": 1},
                "/players": {"players": [{"username": "test_bot", "bot": True}]}}
        if self.path.startswith("/trade?"):
            result = {"stage": "offer", "items": [{"id": 314, "amount": 100}],
                      "otherItems": [{"id": 995, "amount": 50}], "valuationAvailable": False}
        else:
            result = data[self.path]
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(result).encode())


class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.http = HTTPServer(("127.0.0.1", 0), FixtureHandler)
        cls.thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        cls.thread.join()

    def environment(self, token=TOKEN):
        return {"LUNA_COMPANION_TOKEN": token,
                "LUNA_COMPANION_PORT": str(self.http.server_port),
                "HTTP_PROXY": "http://invalid.invalid:1234"}

    def test_stdio_handshake_discovery_and_live_fixture(self):
        messages = [
            {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18"}},
            {"jsonrpc": "2.0", "method": "notifications/initialized"},
            {"jsonrpc": "2.0", "id": 2, "method": "tools/list"},
            {"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {"name": "luna_status"}},
            {"jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": {"name": "luna_inspect_trade", "arguments": {"username": "test bot"}}},
        ]
        proc = subprocess.run([sys.executable, str(ENTRY)],
                              input="".join(json.dumps(m) + "\n" for m in messages),
                              text=True, capture_output=True, timeout=10,
                              env={**os.environ, **self.environment()})
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stderr, "")
        rows = [json.loads(line) for line in proc.stdout.splitlines()]
        self.assertEqual(len(rows), 4)
        self.assertEqual(rows[0]["result"]["protocolVersion"], "2025-06-18")
        self.assertEqual(len(rows[1]["result"]["tools"]), 3)
        self.assertEqual(rows[2]["result"]["structuredContent"]["online"], 2)
        self.assertEqual(rows[3]["result"]["structuredContent"]["otherItems"][0]["amount"], 50)

    def test_failed_authentication_is_tool_error(self):
        with patch.dict(os.environ, self.environment("b" * 48)):
            result = MCP.call_tool({"name": "luna_status"})
        self.assertTrue(result["isError"])
        self.assertIn("401", result["content"][0]["text"])
        self.assertNotIn("b" * 48, json.dumps(result))

    def test_missing_token_is_tool_error(self):
        with patch.dict(os.environ, self.environment("")):
            self.assertTrue(MCP.call_tool({"name": "luna_status"})["isError"])

    def test_invalid_arguments_and_unknown_methods(self):
        for args in [{}, {"username": 123}, {"username": "x" * 13}, {"username": "bot", "extra": 1}]:
            request = {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                       "params": {"name": "luna_inspect_trade", "arguments": args}}
            self.assertEqual(MCP.dispatch(request)["error"]["code"], -32602)
        self.assertEqual(MCP.dispatch({"jsonrpc": "2.0", "id": 1, "method": "unknown"})["error"]["code"], -32601)
        self.assertIsNone(MCP.dispatch({"jsonrpc": "2.0", "method": "notifications/initialized"}))

    def test_parse_error_does_not_stop_stdio(self):
        proc = subprocess.run([sys.executable, str(ENTRY)], input='broken\n{"jsonrpc":"2.0","id":2,"method":"ping"}\n',
                              text=True, capture_output=True, timeout=5)
        rows = [json.loads(line) for line in proc.stdout.splitlines()]
        self.assertEqual(rows[0]["error"]["code"], -32700)
        self.assertEqual(rows[1]["result"], {})


if __name__ == "__main__":
    unittest.main()
