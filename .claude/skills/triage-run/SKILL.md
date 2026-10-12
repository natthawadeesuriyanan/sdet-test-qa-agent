---
name: triage-run
description: Triages a failed nightly run and routes verified product bugs and test defects to guarded issue and draft-PR agents.
disable-model-invocation: true
---
Workflow run ID: `$ARGUMENTS`

1. Read `triage/$ARGUMENTS/failures.json` and `triage/$ARGUMENTS/workflow-jobs.json`. If there are no failed tests, report failed job/step details as a run-level `ENVIRONMENT` finding in `triage/$ARGUMENTS/run-environment.json` and the digest; do not invoke Bug Reporter or Test Fixer or invent a test defect.
2. Read `triage/$ARGUMENTS/ui-traces.json` when present. For each failed test, delegate to `failure-triage`; process no more than five tests concurrently. Include its Surefire report, trace summary, and screenshot paths when present.
3. Have each triage produce one handoff JSON under `triage/$ARGUMENTS/`. Missing or conflicting evidence must produce `UNKNOWN`.
4. For every `PRODUCT_BUG` handoff with confidence at least 0.8 and recorded 3/3 reproduction, delegate to `bug-reporter` to search for duplicates and create one GitHub issue labeled `agent:bug` and `needs-decision`.
5. For every `TEST_DEFECT` handoff with confidence at least 0.8, delegate to `test-fixer` (at most three fixes per run). It may open a draft PR only after the targeted tests and the deterministic quality-policy check pass. Do not route `ENVIRONMENT`, `FLAKY`, or `UNKNOWN` to either write-capable agent.
6. Neither agent may merge, close, or mark work confirmed. If a required secret is missing, required tests cannot run, duplicate search fails, or any validation fails, do not create that issue/PR and state the blocker in the digest.
7. Ask `release-risk-reporter` to write `triage/$ARGUMENTS/digest.md` from verified job outcomes, handoffs, created issue URLs, and draft PR URLs.
