---
name: quality-policy
description: Deterministically rejects oversized, out-of-scope, or test-weakening agent diffs before a fix PR is created.
---
Before opening a test-fix pull request, stage only the intended changes and run:

```bash
python3 .claude/skills/quality-policy/scripts/check_diff.py --base origin/main
git diff --cached --check
```

The checker allows only additions/modifications under `api-tests/src/test/` and `ui-tests/src/test/`, no more than three files or 40 changed lines total, and rejects deletions, removed assertion lines, binary changes, sleeps, generic retry additions, and disabled-test additions. A failure is a hard stop; do not work around or weaken the policy. A human reviewer must still inspect every fix PR before merge.
