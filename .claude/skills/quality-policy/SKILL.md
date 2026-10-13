---
name: quality-policy
description: Deterministically rejects oversized, out-of-scope, or test-weakening agent diffs before a fix PR is created.
---
Before opening a test-fix pull request, stage only the intended changes and run:

```bash
python3 .claude/skills/quality-policy/scripts/check_diff.py --base origin/main
git diff --cached --check
```

The checker allows only additions/modifications under `api-tests/src/test/` and `ui-tests/src/test/`, no more than three files or 40 changed lines total, and rejects deletions, removed assertions that are not replaced by the same kind of assertion in the same file (correcting an expected value is allowed; dropping a check is not), binary changes, sleeps, generic retry additions, and disabled-test additions. A failure is a hard stop; do not work around or weaken the policy. A human reviewer must still inspect every fix PR before merge.
