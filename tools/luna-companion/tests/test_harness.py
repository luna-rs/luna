import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("harness", Path(__file__).resolve().parents[1] / "server/harness.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class HarnessTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.harness = MODULE.Harness(self.temp.name)

    def tearDown(self):
        self.harness.close()
        self.temp.cleanup()

    def launch_fixture(self, program):
        with patch.object(self.harness, "_check"), patch.object(self.harness, "_environment", return_value={**os.environ, "LUNA_COMPANION_TOKEN": "private-fixture-token"}), patch.object(self.harness, "_command", return_value=[sys.executable, "-u", "-c", program]):
            return self.harness.start("runtime")

    def wait_exit(self, job):
        deadline = time.monotonic() + 10
        while self.harness.job(job["id"])["running"] and time.monotonic() < deadline:
            time.sleep(.02)
        self.assertFalse(self.harness.job(job["id"])["running"])
        # Capture finishes independently of process exit.
        while not any(e["event"] == "exited" for e in self.harness.status()["events"]) and time.monotonic() < deadline:
            time.sleep(.02)

    def test_real_process_output_redaction_and_exit(self):
        job = self.launch_fixture("print('private-fixture-token'); print('password=hidden'); raise SystemExit(7)")
        self.wait_exit(job)
        logs = str(self.harness.logs(job["id"]))
        self.assertNotIn("private-fixture-token", logs)
        self.assertNotIn("hidden", logs)
        self.assertIn("REDACTED", logs)
        self.assertEqual(self.harness.job(job["id"])["exitCode"], 7)

    def test_bounded_logs_cursor_and_oversized_line(self):
        job = self.launch_fixture("print('x'*20000); [print(i) for i in range(2200)]")
        self.wait_exit(job)
        logs = self.harness.logs(job["id"], limit=200)
        self.assertTrue(logs["truncated"])
        self.assertEqual(len(logs["lines"]), 200)
        self.assertEqual(len(self.harness.jobs[job["id"]]["lines"]), 2000)
        self.assertGreater(self.harness.logs(job["id"], logs["nextAfter"])["nextAfter"], logs["nextAfter"])

    def test_stop_owned_process_and_reject_foreign_job(self):
        with self.assertRaises(ValueError):
            self.harness.stop("1234")
        job = self.launch_fixture("import time; print('ready'); time.sleep(60)")
        self.assertTrue(job["running"])
        self.assertFalse(self.harness.stop(job["id"])["running"])

    def test_clone_branch_guard_and_argument_validation(self):
        (Path(self.temp.name) / "gradlew.bat").touch()
        with patch.object(MODULE.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "master\n", "")):
            with self.assertRaises(ValueError):
                self.harness._check()
        with patch.object(self.harness, "_check"):
            with self.assertRaises(ValueError):
                self.harness.start("tests", "--init-script evil")
        with self.assertRaises(ValueError):
            self.harness.logs("unknown", limit=10000)

    def test_close_stops_owned_runtime(self):
        job = self.launch_fixture("import time; time.sleep(60)")
        self.harness.close()
        self.assertFalse(self.harness.job(job["id"])["running"])

    def test_drops_entire_oversized_line(self):
        job = self.launch_fixture("print('x'*16380 + 'private-fixture-token' + 'z'*100); print('done')")
        self.wait_exit(job)
        logs = str(self.harness.logs(job["id"]))
        self.assertIn("oversized log line omitted", logs)
        self.assertNotIn("private-fixture", logs)
        self.assertIn("done", logs)


if __name__ == "__main__":
    unittest.main()
