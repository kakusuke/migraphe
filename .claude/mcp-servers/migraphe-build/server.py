#!/usr/bin/env python3
"""MCP server exposing Migraphe gradle build/test commands.

Speaks MCP over stdio (newline-delimited JSON-RPC 2.0).
Stdlib only — no external dependencies.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

PROTOCOL_VERSION = "2024-11-05"
SERVER_NAME = "migraphe-build"
SERVER_VERSION = "0.1.0"

OUTPUT_BUDGET_BYTES = 8000
TAIL_LINES = 50
SUBPROCESS_TIMEOUT_SEC = 900
LOG_DIR = Path("/tmp/migraphe-build-mcp")


@dataclass
class CommandResult:
    exit_code: int
    stdout: str
    stderr: str
    duration_seconds: float
    log_path: str


def run_gradle(project_root: Path, gradle_args: list[str]) -> CommandResult:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%dT%H%M%S")
    safe_label = re.sub(r"[^A-Za-z0-9_.-]+", "_", "_".join(gradle_args) or "cmd")
    log_path = LOG_DIR / f"{timestamp}-{safe_label}.log"

    cmd = ["./gradlew", *gradle_args]
    start = time.monotonic()
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(project_root),
            capture_output=True,
            text=True,
            timeout=SUBPROCESS_TIMEOUT_SEC,
            check=False,
        )
        duration = time.monotonic() - start
        stdout = proc.stdout or ""
        stderr = proc.stderr or ""
        exit_code = proc.returncode
    except subprocess.TimeoutExpired as e:
        duration = time.monotonic() - start
        stdout = (e.stdout or b"").decode("utf-8", errors="replace") if isinstance(e.stdout, bytes) else (e.stdout or "")
        stderr = (e.stderr or b"").decode("utf-8", errors="replace") if isinstance(e.stderr, bytes) else (e.stderr or "")
        stderr += f"\n[TIMEOUT after {SUBPROCESS_TIMEOUT_SEC}s]"
        exit_code = -1

    log_path.write_text(
        f"$ {' '.join(cmd)}\n[exit={exit_code} duration={duration:.1f}s]\n\n"
        f"--- STDOUT ---\n{stdout}\n--- STDERR ---\n{stderr}\n",
        encoding="utf-8",
    )
    return CommandResult(
        exit_code=exit_code,
        stdout=stdout,
        stderr=stderr,
        duration_seconds=duration,
        log_path=str(log_path),
    )


def tail_lines(text: str, n: int) -> str:
    lines = text.splitlines()
    return "\n".join(lines[-n:])


def extract_test_failures(stdout: str, stderr: str) -> list[str]:
    """Extract gradle test failure markers like `> Task :foo:test FAILED` and JUnit summary lines."""
    failures: list[str] = []
    combined = stdout + "\n" + stderr
    for line in combined.splitlines():
        if "FAILED" in line and ("Task " in line or "> " in line):
            failures.append(line.strip())
        elif re.search(r"\d+ tests? completed,? \d+ failed", line):
            failures.append(line.strip())
    return failures[:20]


def extract_warnings(stdout: str, stderr: str) -> list[str]:
    """Extract Japanese-warning lines (`./gradlew ... | grep 警告` equivalent)."""
    warnings: list[str] = []
    combined = stdout + "\n" + stderr
    for line in combined.splitlines():
        if "警告:" in line or " warning:" in line.lower():
            warnings.append(line.strip())
    return warnings[:50]


def shrink_payload(payload: dict[str, Any]) -> dict[str, Any]:
    """If serialized payload exceeds OUTPUT_BUDGET_BYTES, drop/trim tail."""
    serialized = json.dumps(payload, ensure_ascii=False)
    if len(serialized.encode("utf-8")) <= OUTPUT_BUDGET_BYTES:
        return payload
    payload = dict(payload)
    tail = payload.get("tail", "")
    while tail and len(json.dumps(payload, ensure_ascii=False).encode("utf-8")) > OUTPUT_BUDGET_BYTES:
        tail_lines_list = tail.splitlines()
        if len(tail_lines_list) <= 5:
            payload["tail"] = "[truncated — see log_path for full output]"
            break
        tail = "\n".join(tail_lines_list[len(tail_lines_list) // 2 :])
        payload["tail"] = tail
    return payload


def build_result_payload(
    cmd_label: str,
    res: CommandResult,
    *,
    test_failures: list[str] | None = None,
    warnings: list[str] | None = None,
) -> dict[str, Any]:
    status = "success" if res.exit_code == 0 else "failed"
    summary_bits = [f"{cmd_label}: {status}", f"exit={res.exit_code}", f"{res.duration_seconds:.1f}s"]
    if test_failures:
        summary_bits.append(f"{len(test_failures)} test failure marker(s)")
    if warnings:
        summary_bits.append(f"{len(warnings)} warning(s)")
    payload: dict[str, Any] = {
        "exit_code": res.exit_code,
        "status": status,
        "duration_seconds": round(res.duration_seconds, 2),
        "summary": " | ".join(summary_bits),
        "tail": tail_lines(res.stdout + "\n" + res.stderr, TAIL_LINES),
        "log_path": res.log_path,
    }
    if test_failures:
        payload["test_failures"] = test_failures
    if warnings:
        payload["warnings"] = warnings
    return shrink_payload(payload)


# --- Tool definitions ----------------------------------------------------------

TOOLS: list[dict[str, Any]] = [
    {
        "name": "run_test",
        "description": (
            "Run `./gradlew test` on the Migraphe project. Optionally scope to a single module "
            "(e.g. `migraphe-core`) and/or a JUnit test filter (e.g. `*DagExecutor*`). "
            "Returns exit code, duration, tail of output, extracted test failure markers, and a log file path. "
            "Use this whenever you need to verify red/green status during a TDD phase."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "module": {
                    "type": "string",
                    "description": "Gradle module name without the leading colon (e.g. `migraphe-core`). Omit to run tests on all modules.",
                },
                "test_filter": {
                    "type": "string",
                    "description": "JUnit class/method pattern passed via `--tests`. Example: `io.github.kakusuke.migraphe.core.execution.DagExecutorSequentialUpTest`.",
                },
            },
            "additionalProperties": False,
        },
    },
    {
        "name": "run_build",
        "description": (
            "Run `./gradlew build` on the Migraphe project (compiles + runs all tests + checks). "
            "Use sparingly — prefer `run_test` for per-cycle verification."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {},
            "additionalProperties": False,
        },
    },
    {
        "name": "run_spotless",
        "description": (
            "Run `./gradlew spotlessApply` to auto-format Java sources. "
            "MUST be run before commit per Migraphe CLAUDE.md."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {},
            "additionalProperties": False,
        },
    },
    {
        "name": "run_errorprone_check",
        "description": (
            "Run `./gradlew clean build --warning-mode all` and extract ErrorProne / javac warning lines "
            "(matches the `grep 警告` check in CLAUDE.md). Use as the final gate before commit."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {},
            "additionalProperties": False,
        },
    },
]


def handle_run_test(project_root: Path, args: dict[str, Any]) -> dict[str, Any]:
    module = args.get("module")
    test_filter = args.get("test_filter")
    gradle_args: list[str] = []
    if module:
        gradle_args.append(f":{module}:test")
    else:
        gradle_args.append("test")
    if test_filter:
        gradle_args += ["--tests", str(test_filter)]
    res = run_gradle(project_root, gradle_args)
    return build_result_payload("test", res, test_failures=extract_test_failures(res.stdout, res.stderr))


def handle_run_build(project_root: Path, _args: dict[str, Any]) -> dict[str, Any]:
    res = run_gradle(project_root, ["build"])
    return build_result_payload("build", res, test_failures=extract_test_failures(res.stdout, res.stderr))


def handle_run_spotless(project_root: Path, _args: dict[str, Any]) -> dict[str, Any]:
    res = run_gradle(project_root, ["spotlessApply"])
    return build_result_payload("spotlessApply", res)


def handle_run_errorprone_check(project_root: Path, _args: dict[str, Any]) -> dict[str, Any]:
    res = run_gradle(project_root, ["clean", "build", "--warning-mode", "all"])
    return build_result_payload("errorprone_check", res, warnings=extract_warnings(res.stdout, res.stderr))


HANDLERS = {
    "run_test": handle_run_test,
    "run_build": handle_run_build,
    "run_spotless": handle_run_spotless,
    "run_errorprone_check": handle_run_errorprone_check,
}


# --- JSON-RPC plumbing ---------------------------------------------------------


def write_message(msg: dict[str, Any]) -> None:
    sys.stdout.write(json.dumps(msg, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def make_response(req_id: Any, result: dict[str, Any]) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": req_id, "result": result}


def make_error(req_id: Any, code: int, message: str) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}


def handle_request(project_root: Path, req: dict[str, Any]) -> dict[str, Any] | None:
    method = req.get("method")
    req_id = req.get("id")
    params = req.get("params") or {}

    if method == "initialize":
        return make_response(
            req_id,
            {
                "protocolVersion": PROTOCOL_VERSION,
                "capabilities": {"tools": {}},
                "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
            },
        )

    if method == "tools/list":
        return make_response(req_id, {"tools": TOOLS})

    if method == "tools/call":
        name = params.get("name")
        arguments = params.get("arguments") or {}
        handler = HANDLERS.get(name)
        if handler is None:
            return make_error(req_id, -32601, f"unknown tool: {name}")
        try:
            payload = handler(project_root, arguments)
        except Exception as e:  # noqa: BLE001
            return make_response(
                req_id,
                {
                    "content": [{"type": "text", "text": f"tool error: {type(e).__name__}: {e}"}],
                    "isError": True,
                },
            )
        is_error = payload.get("status") == "failed"
        return make_response(
            req_id,
            {
                "content": [{"type": "text", "text": json.dumps(payload, ensure_ascii=False, indent=2)}],
                "isError": is_error,
            },
        )

    if method == "ping":
        return make_response(req_id, {})

    if method and method.startswith("notifications/"):
        return None  # notifications expect no response

    if req_id is None:
        return None  # other notifications

    return make_error(req_id, -32601, f"method not found: {method}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--project-root",
        default=os.environ.get("MIGRAPHE_PROJECT_ROOT", "."),
        help="Migraphe project root (where ./gradlew lives). Default: cwd.",
    )
    cli_args = parser.parse_args()
    project_root = Path(cli_args.project_root).resolve()

    if not (project_root / "gradlew").exists():
        sys.stderr.write(
            f"[migraphe-build-mcp] gradlew not found under {project_root}; "
            "set --project-root or MIGRAPHE_PROJECT_ROOT.\n"
        )
        return 2

    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            req = json.loads(raw)
        except json.JSONDecodeError as e:
            sys.stderr.write(f"[migraphe-build-mcp] bad JSON: {e}\n")
            continue
        response = handle_request(project_root, req)
        if response is not None:
            write_message(response)
    return 0


if __name__ == "__main__":
    sys.exit(main())
