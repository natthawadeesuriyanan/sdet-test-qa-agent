#!/usr/bin/env python3
"""Extract failed JUnit/Surefire cases to JSON without external dependencies."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


AUTHORIZATION = re.compile(
    r"(?i)(authorization\s*[:=]\s*)(?:bearer\s+)?[^\s,;<>]+"
)
BEARER = re.compile(r"(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{12,}")
TOKEN_PARAMETER = re.compile(
    r"(?i)\b(access_token|api[_-]?token)\s*([=:]\s*)[^\s&;,]+"
)


def redact(value: str) -> str:
    value = AUTHORIZATION.sub(r"\1******", value)
    value = BEARER.sub("Bearer ******", value)
    value = TOKEN_PARAMETER.sub(r"\1\2******", value)
    for variable in ("GOREST_API_TOKEN", "ANTHROPIC_API_KEY"):
        secret = os.environ.get(variable)
        if secret:
            value = value.replace(secret, "******")
    return value


def report_files(report_path: Path) -> list[Path]:
    if report_path.is_file():
        return [report_path]
    if report_path.is_dir():
        return sorted(report_path.rglob("TEST-*.xml"))
    raise FileNotFoundError(f"Surefire report path does not exist: {report_path}")


def extract_failures(files: list[Path], run_id: str) -> dict[str, Any]:
    failures: list[dict[str, str]] = []
    test_count = 0

    for report in files:
        root = ET.parse(report).getroot()
        relative_report = str(report)
        for case in root.iter("testcase"):
            test_count += 1
            classname = case.attrib.get("classname", "")
            method = case.attrib.get("name", "")
            test_id = f"{classname}.{method}" if classname else method
            for element in case:
                if element.tag not in {"failure", "error"}:
                    continue
                failures.append(
                    {
                        "test_id": redact(test_id),
                        "classname": redact(classname),
                        "method": redact(method),
                        "report": redact(relative_report),
                        "kind": element.tag,
                        "type": redact(element.attrib.get("type", "")),
                        "message": redact(element.attrib.get("message", "")),
                        "details": redact((element.text or "").strip()),
                    }
                )

    return {
        "schema_version": 1,
        "run_id": run_id,
        "summary": {
            "reports": len(files),
            "tests": test_count,
            "failures": len(failures),
        },
        "failures": failures,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path, help="Surefire XML file or directory")
    parser.add_argument("--run-id", default="local", help="CI workflow run ID")
    parser.add_argument("--output", type=Path, help="JSON output path (defaults to stdout)")
    args = parser.parse_args()

    files = report_files(args.reports)
    if not files:
        parser.error(f"no TEST-*.xml reports found under {args.reports}")
    result = extract_failures(files, args.run_id)
    serialized = json.dumps(result, indent=2, ensure_ascii=False) + "\n"

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized, encoding="utf-8")
        print(
            f"Parsed {result['summary']['reports']} report(s), "
            f"{result['summary']['tests']} test(s), "
            f"{result['summary']['failures']} failure(s) -> {args.output}",
            file=sys.stderr,
        )
    else:
        sys.stdout.write(serialized)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
