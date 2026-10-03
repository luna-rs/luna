"""Session-owned development processes. No arbitrary command or PID tool surface."""
from collections import deque
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import threading
import time
import uuid
import xml.etree.ElementTree as ET


class Harness:
    def __init__(self, root=None):
        self.root = Path(root or os.environ.get("LUNA_COMPANION_REPO", "")).resolve()
        self.configured = root is not None or bool(os.environ.get("LUNA_COMPANION_REPO"))
        self.jobs = {}
        self.lock = threading.RLock()
        self.events = deque(maxlen=200)

    def _check(self):
        if not self.configured or not (self.root / "gradlew.bat").is_file():
            raise ValueError("Configure LUNA_COMPANION_REPO to the dedicated Luna clone.")
        branch = subprocess.run(["git", "branch", "--show-current"], cwd=self.root,
                                capture_output=True, text=True, timeout=5)
        if branch.returncode or not branch.stdout.strip().startswith("codex/"):
            raise ValueError("Runtime actions require a codex/* branch in the configured clone.")

    def _environment(self):
        env = dict(os.environ)
        java = env.get("LUNA_COMPANION_JAVA_HOME", env.get("JAVA_HOME"))
        if not java:
            raise ValueError("Configure LUNA_COMPANION_JAVA_HOME with JDK 21.")
        env["JAVA_HOME"] = java
        env["GRADLE_USER_HOME"] = str(Path.home() / ".gradle")
        env["LUNA_COMPANION_TOKEN"] = (Path.home() / ".luna-companion" / "token").read_text().strip()
        return env

    def _redactor(self, env):
        values = [v for k, v in env.items() if re.search(r"token|password|secret|api.?key", k, re.I) and len(v) >= 6]
        config = self.root / "data" / "luna.jsonc"
        if config.exists():
            values += re.findall(r'"(?:password|token|secret|api_key)"\s*:\s*"([^"\r\n]+)"', config.read_text(), re.I)
        def redact(text):
            for value in sorted(set(values), key=len, reverse=True):
                text = text.replace(value, "[REDACTED]")
            text = re.sub(r'(?i)(bearer\s+)[A-Za-z0-9_.-]+', r'\1[REDACTED]', text)
            return re.sub(r'(?i)((?:password|token|secret|api_key)\s*[=:]\s*)\S+', r'\1[REDACTED]', text)
        return redact

    def _command(self, kind, test_filter, env):
        java = str(Path(env["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))
        if kind == "runtime":
            libraries = self.root / "build" / "install" / "luna" / "lib"
            if not libraries.is_dir():
                raise ValueError("Run luna_build and wait for exitCode 0 before starting Luna.")
            return [java, "-cp", str(libraries / "*"), "io.luna.Luna"]
        command = [java, "-classpath", str(self.root / "gradle/wrapper/gradle-wrapper.jar"),
                   "org.gradle.wrapper.GradleWrapperMain", "test" if kind == "tests" else "installDist",
                   "--console=plain", "--no-daemon"]
        if test_filter:
            command += ["--tests", test_filter]
        if kind == "tests":
            command += ["--rerun-tasks"]
        return command

    def start(self, kind, test_filter=None):
        self._check()
        if kind not in ("runtime", "tests", "build"):
            raise ValueError("Unknown job kind.")
        if test_filter is not None and (not isinstance(test_filter, str) or
                not re.fullmatch(r"[A-Za-z0-9_.$*]{1,200}", test_filter) or kind != "tests"):
            raise ValueError("Test filter must be a class/method pattern, not command arguments.")
        with self.lock:
            if any(j["process"].poll() is None for j in self.jobs.values()):
                raise ValueError("A harness job is already running. Wait for it or stop it first.")
            env = self._environment()
            command = self._command(kind, test_filter, env)
            redact = self._redactor(env)
            process = subprocess.Popen(command, cwd=self.root, env=env, stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
                start_new_session=os.name != "nt")
            job_id = uuid.uuid4().hex[:12]
            job = {"id": job_id, "kind": kind, "process": process, "started": time.time(),
                   "lines": deque(maxlen=2000), "sequence": 0, "redact": redact}
            self.jobs[job_id] = job
            # Retain at most 20 completed jobs in memory.
            while len(self.jobs) > 20:
                del self.jobs[next(iter(self.jobs))]
            self.events.append({"time": time.time(), "event": "started", "job": job_id, "kind": kind})
            threading.Thread(target=self._capture, args=(job,), daemon=True).start()
            return self.job(job_id)

    def _capture(self, job):
        # A capped binary readline avoids unbounded buffering of malformed log lines.
        stream = job["process"].stdout
        try:
            while True:
                chunk = stream.readline(16384)
                if not chunk:
                    break
                # Discard oversized lines rather than splitting a secret across output chunks.
                if len(chunk) == 16384 and not chunk.endswith(b"\n"):
                    while chunk and not chunk.endswith(b"\n"):
                        chunk = stream.readline(16384)
                    text = "[oversized log line omitted]"
                else:
                    text = job["redact"](chunk.decode("utf-8", errors="replace").rstrip())[:4096]
                with self.lock:
                    job["sequence"] += 1
                    job["lines"].append({"sequence": job["sequence"], "text": text})
        finally:
            stream.close()
            code = job["process"].wait()
            with self.lock:
                self.events.append({"time": time.time(), "event": "exited", "job": job["id"], "exitCode": code})

    def _get(self, job_id):
        if not isinstance(job_id, str) or job_id not in self.jobs:
            raise ValueError("Unknown job in this MCP session.")
        return self.jobs[job_id]

    def job(self, job_id):
        with self.lock:
            job = self._get(job_id)
            code = job["process"].poll()
            result = {"id": job_id, "kind": job["kind"], "pid": job["process"].pid,
                      "running": code is None, "exitCode": code, "started": job["started"],
                      "lastSequence": job["sequence"]}
            if code is not None and job["kind"] == "tests":
                suites = []
                for path in sorted((self.root / "build/test-results/test").glob("TEST-*.xml"))[:200]:
                    if path.stat().st_mtime < job["started"] or path.stat().st_size > 2_000_000:
                        continue
                    try:
                        suite = ET.parse(path).getroot()
                        suites.append({k: job["redact"](suite.get(k, ""))[:300]
                                       for k in ("name", "tests", "failures", "errors", "skipped", "time")})
                    except ET.ParseError:
                        continue
                result["testSuites"] = suites
            return result

    def logs(self, job_id, after=0, limit=100):
        if type(after) is not int or after < 0 or type(limit) is not int or not 1 <= limit <= 200:
            raise ValueError("after must be nonnegative; limit must be 1–200.")
        with self.lock:
            job = self._get(job_id)
            lines = [line for line in job["lines"] if line["sequence"] > after][:limit]
            return {"job": job_id, "lines": lines,
                    "nextAfter": lines[-1]["sequence"] if lines else after,
                    "truncated": bool(job["lines"] and after < job["lines"][0]["sequence"] - 1)}

    def stop(self, job_id):
        with self.lock:
            job = self._get(job_id)
            process = job["process"]
            if process.poll() is None:
                if os.name == "nt" and job["kind"] == "runtime":
                    # Runtime is a direct JVM, so use its retained process handle.
                    process.terminate()
                elif os.name == "nt":
                    stopped = subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                                   capture_output=True, timeout=15, creationflags=subprocess.CREATE_NO_WINDOW)
                    if stopped.returncode and process.poll() is None:
                        raise OSError("Could not stop owned Gradle process tree.")
                else:
                    os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=15)
            return self.job(job_id)

    def status(self):
        with self.lock:
            return {"root": str(self.root), "configured": self.configured,
                    "jobs": [self.job(job_id) for job_id in self.jobs], "events": list(self.events),
                    "lifecycle": "Jobs are owned by this MCP session and stopped when it closes."}

    def close(self):
        for job_id in list(self.jobs):
            try:
                self.stop(job_id)
            except (OSError, subprocess.TimeoutExpired):
                pass
