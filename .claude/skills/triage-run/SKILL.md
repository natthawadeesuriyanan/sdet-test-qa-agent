---
name: triage-run
description: Produces shadow-mode triage and a risk digest from evidence for one failed nightly workflow run.
disable-model-invocation: true
---
Workflow run ID: `$ARGUMENTS`

1. Read `triage/$ARGUMENTS/failures.json`, produced from the downloaded Surefire artifacts by `log-analysis`.
2. Read `triage/$ARGUMENTS/ui-traces.json` when present. For each failed test, delegate to the `failure-triage` subagent; process no more than five tests concurrently. Include the relevant original Surefire report, trace summary, and UI screenshot paths when present.
3. Have each triage produce one handoff JSON under `triage/$ARGUMENTS/`. Preserve the output contract in the agent definition. Missing or conflicting evidence must produce `UNKNOWN`.
4. For each handoff classified `PRODUCT_BUG` with confidence at least 0.8, delegate to `bug-reporter` to create a human-reviewable issue draft under `triage/$ARGUMENTS/issue-drafts/`. The reporter must not publish GitHub issues. Do not invoke the reporter for other verdicts or lower-confidence results.
5. Do not rerun tests automatically, open issues or pull requests, push changes, or merge changes. This workflow is the shadow phase and its GitHub token has read-only repository permissions.
6. Ask `release-risk-reporter` to write `triage/$ARGUMENTS/digest.md` from the verified results, handoffs, and any issue drafts. The digest and drafts are artifacts, not GitHub issues.
