---
name: triage-run
description: Produces shadow-mode triage and a risk digest from evidence for one failed nightly workflow run.
disable-model-invocation: true
---
Workflow run ID: `$ARGUMENTS`

1. Read `triage/$ARGUMENTS/failures.json`, produced from the downloaded Surefire artifacts by `log-analysis`.
2. Read `triage/$ARGUMENTS/ui-traces.json` when present. For each failed test, delegate to the `failure-triage` subagent; process no more than five tests concurrently. Include the relevant original Surefire report, trace summary, and UI screenshot paths when present.
3. Have each triage produce one handoff JSON under `triage/$ARGUMENTS/`. Preserve the output contract in the agent definition. Missing or conflicting evidence must produce `UNKNOWN`.
4. Do not rerun tests automatically, open issues or pull requests, push changes, or invoke downstream agents. This workflow is the shadow phase and its GitHub token has read-only repository permissions.
5. Ask `release-risk-reporter` to write `triage/$ARGUMENTS/digest.md` from the verified results and handoffs. The digest is an artifact, not an issue.
