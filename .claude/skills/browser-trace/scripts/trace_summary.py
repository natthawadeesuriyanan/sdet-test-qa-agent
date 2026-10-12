#!/usr/bin/env python3
"""Summarize Playwright trace archives without copying headers or form values."""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "log-analysis" / "scripts"))
from parse_junit import redact

MAX_TRACE_BYTES = 20 * 1024 * 1024
MAX_EVENTS_PER_TRACE = 200


def trace_files(path: Path) -> list[Path]:
    if path.is_file():
        return [path]
    if path.is_dir():
        return sorted(path.rglob("*.zip"))
    raise FileNotFoundError(f"Trace path does not exist: {path}")


def safe_event(record: dict[str, Any]) -> dict[str, Any] | None:
    event_type = record.get("type")
    summary: dict[str, Any] = {"type": event_type}

    api_name = record.get("apiName")
    if isinstance(api_name, str):
        summary["api"] = redact(api_name)
    for key in ("class", "method", "pageId"):
        value = record.get(key)
        if isinstance(value, (str, int)):
            summary[key] = redact(str(value))

    url = record.get("url")
    if not isinstance(url, str):
        snapshot = record.get("snapshot")
        request = snapshot.get("request") if isinstance(snapshot, dict) else None
        url = request.get("url") if isinstance(request, dict) else None
    if isinstance(url, str):
        summary["url"] = redact(url)

    snapshot = record.get("snapshot")
    response = snapshot.get("response") if isinstance(snapshot, dict) else None
    status = record.get("status")
    if status is None and isinstance(response, dict):
        status = response.get("status")
    if isinstance(status, (int, float)):
        summary["status"] = status

    error = record.get("error")
    if isinstance(error, dict):
        error_type = error.get("name") or error.get("type")
        message = error.get("message")
        if isinstance(error_type, str):
            summary["error_type"] = redact(error_type)
        if isinstance(message, str):
            summary["error"] = redact(message)
    elif isinstance(error, str):
        summary["error"] = redact(error)

    if event_type == "console" and record.get("messageType") == "error":
        text = record.get("text")
        if isinstance(text, str):
            summary["error"] = redact(text)

    if event_type in {"before", "after", "console", "resource-snapshot"} or "error" in summary:
        return summary
    return None


def summarize_archive(path: Path) -> dict[str, Any]:
    events: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []
    trace_entries = 0

    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if not info.filename.endswith(".trace"):
                continue
            if info.file_size > MAX_TRACE_BYTES:
                raise ValueError(f"Trace entry exceeds {MAX_TRACE_BYTES} bytes: {info.filename}")
            trace_entries += 1
            with archive.open(info) as trace:
                for line_number, line in enumerate(trace, start=1):
                    if line_number > 100_000:
                        raise ValueError(f"Trace contains too many records: {info.filename}")
                    if not line.strip():
                        continue
                    record = json.loads(line)
                    if not isinstance(record, dict):
                        continue
                    event = safe_event(record)
                    if event is None:
                        continue
                    events.append(event)
                    if "error" in event:
                        errors.append(event)

    if trace_entries == 0:
        raise ValueError(f"No Playwright .trace entries found in {path}")

    retained = events[-MAX_EVENTS_PER_TRACE:]
    for event in errors:
        if event not in retained:
            retained.append(event)
    return {
        "archive": redact(str(path)),
        "trace_entries": trace_entries,
        "events_retained": len(retained),
        "events_omitted": max(0, len(events) - MAX_EVENTS_PER_TRACE),
        "errors": errors,
        "events": retained,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("traces", type=Path, help="Playwright trace ZIP file or directory")
    parser.add_argument("--output", type=Path, help="JSON output path (defaults to stdout)")
    args = parser.parse_args()

    archives = trace_files(args.traces)
    result = {
        "schema_version": 1,
        "archives": [summarize_archive(path) for path in archives],
    }
    serialized = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized, encoding="utf-8")
        print(f"Summarized {len(archives)} trace archive(s) -> {args.output}", file=sys.stderr)
    else:
        sys.stdout.write(serialized)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
