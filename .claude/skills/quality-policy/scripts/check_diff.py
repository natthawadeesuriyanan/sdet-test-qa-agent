#!/usr/bin/env python3
"""Apply deterministic scope and size checks to a staged test-only repair."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import Counter


ASSERTION = re.compile(r"\b(assert[A-Z]\w*|assertThat(?:ThrownBy|Code)?)\s*\(")
FORBIDDEN_ADDITION = re.compile(
    r"\bThread\s*\.\s*sleep\s*\(|\bwaitForTimeout\s*\(|@Disabled\b|@RetryingTest\b"
)


def git(*arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"git {' '.join(arguments)} failed")
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="Base ref, normally origin/main")
    args = parser.parse_args()

    try:
        unstaged = subprocess.run(
            ["git", "diff", "--quiet"],
            check=False,
            capture_output=True,
        )
        if unstaged.returncode == 1:
            raise ValueError("unstaged tracked changes found; stage the exact proposed diff first")
        if unstaged.returncode > 1:
            raise RuntimeError("could not inspect unstaged changes")
        if git("ls-files", "--others", "--exclude-standard").strip():
            raise ValueError("untracked files found; stage or remove them before validation")

        name_status = git(
            "diff", "--cached", "--name-status", "--no-renames", args.base
        ).splitlines()
        if not name_status:
            raise ValueError("no staged changes found")
        if len(name_status) > 3:
            raise ValueError(f"change touches {len(name_status)} files; maximum is 3")

        paths: list[str] = []
        for record in name_status:
            status, path = record.split("\t", 1)
            if status not in {"A", "M"}:
                raise ValueError(f"deletions, renames, and other changes are prohibited: {record}")
            if not path.startswith(("ui-tests/src/test/", "api-tests/src/test/")):
                raise ValueError(f"path is outside the test-only allowlist: {path}")
            paths.append(path)

        numstat = git("diff", "--cached", "--numstat", args.base).splitlines()
        changed_lines = 0
        for record in numstat:
            additions, deletions, path = record.split("\t", 2)
            if additions == "-" or deletions == "-":
                raise ValueError(f"binary changes are prohibited: {path}")
            changed_lines += int(additions) + int(deletions)
        if changed_lines > 40:
            raise ValueError(f"change contains {changed_lines} added/deleted lines; maximum is 40")

        patch = git("diff", "--cached", "--no-ext-diff", "--unified=0", args.base)
        violations: list[str] = []
        # An assertion may be corrected (e.g. assertStatus(r, 404) -> assertStatus(r, 422)),
        # but every removed assertion call must be replaced by the same kind of call in the
        # same file. A net loss of any assertion kind is a weakened test and is rejected.
        removed: dict[str, Counter] = {}
        added: dict[str, Counter] = {}
        current = ""
        for line in patch.splitlines():
            if line.startswith("+++ "):
                current = line[6:] if line.startswith("+++ b/") else line[4:]
                continue
            if line.startswith("---"):
                continue
            if line.startswith("-"):
                removed.setdefault(current, Counter()).update(m.group(1) for m in ASSERTION.finditer(line[1:]))
            if line.startswith("+"):
                added.setdefault(current, Counter()).update(m.group(1) for m in ASSERTION.finditer(line[1:]))
                if FORBIDDEN_ADDITION.search(line[1:]):
                    violations.append("added a sleep, retry annotation, or disabled test")
        for path, kinds in removed.items():
            missing = kinds - added.get(path, Counter())
            if missing:
                violations.append(f"removed assertion line(s) without replacement in {path}: {dict(missing)}")
        if violations:
            raise ValueError(", ".join(sorted(set(violations))))

        print(
            f"PASS: {len(paths)} test file(s), {changed_lines} added/deleted lines, "
            "all staged changes are within policy."
        )
        return 0
    except (RuntimeError, ValueError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
