---
name: triage-run
description: Triages a failed nightly run, raises every impediment as a GitHub issue, and opens review-ready fix PRs for automation script errors.
disable-model-invocation: true
---
Workflow run ID: `$ARGUMENTS`

1. Read `triage/$ARGUMENTS/failures.json` and `triage/$ARGUMENTS/workflow-jobs.json`. A failed job whose suite produced no failed tests (e.g. missing secret, compile error, runner failure) is a run-level `ENVIRONMENT` finding: record it in `triage/$ARGUMENTS/run-environment.json` with a fingerprint of `<job name> + <failed step>`. A compile error in test code that clearly points at the test source is a `TEST_DEFECT` instead.
2. Read `triage/$ARGUMENTS/ui-traces.json` when present. For each failed test, delegate to `failure-triage`; process no more than five tests concurrently. Include its Surefire report, trace summary, and screenshot paths when present.
3. Each triage produces one handoff JSON under `triage/$ARGUMENTS/`. Missing or conflicting evidence must produce `UNKNOWN`.
4. Raise impediments: delegate every handoff and every run-level finding to `issue-reporter`, which creates one deduplicated issue per fingerprint (or comments on the existing one). Group failures that share a fingerprint into one issue.
5. Fix automation script errors: for every `TEST_DEFECT` handoff with confidence at least 0.8, after its issue exists, delegate to `test-fixer` (at most three per run) with the issue number and the PR reviewer from the prompt. It opens a PR only after the targeted tests and the deterministic quality-policy check pass. Do not route `PRODUCT_BUG`, `ENVIRONMENT`, `FLAKY`, or `UNKNOWN` to `test-fixer`.
6. No agent may merge, approve, close, or mark work confirmed. If duplicate search fails, required tests cannot run, or validation fails, skip that issue/PR and record the blocker in `triage/$ARGUMENTS/blockers.md`.
7. Finish with a short `triage/$ARGUMENTS/summary.md`: failures by verdict with links to issues and PRs. The Morning digest job turns this into the digest.
