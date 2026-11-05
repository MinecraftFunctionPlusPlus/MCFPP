<!-- codex-workflow-resume:begin -->
## Saved workflow recovery
This block applies only when the coordinating agent continues the saved workflow below. Delegated worker agents should follow their assigned subtask; unrelated work is unaffected.
Project: F:\Project_MCFPP\MCFPP
Saved workflow: 01b97d67-a4fe-4c04-a49c-c65ea9f204b0
When continuing this workflow, read `.codex-workflow-graph/RESUME.md`, use `$codex-workflow-graph:workflow-graph`, refresh its read-only doctor/resume snapshot, reopen its exact dashboard page, and reconcile native agent IDs before dispatching work.
<!-- codex-workflow-resume:end -->

<!-- codex-workflow-budget:begin -->
## Effective workflow budgets
For the saved workflow only, read `.codex-workflow-graph/BUDGET_POLICY.json` before scheduling or beginning validation. The user's last explicit numerical approval was 20 hours / 160 attempts. Under the user's subsequent full delegation to finish the goal and request to accelerate, the coordinator allocated 176 records and a 116640-second raw ledger ceiling for the bounded remaining fixes and targeted checks; the time adjustment accounts for recorded post-interruption idle. These operational numbers were chosen by the coordinator, not explicitly selected by the user. Preserve all prior approvals, consumption, two-no-gain replanning and repeated-check limits. Project concurrency remains 8; no additional full regression is planned.
<!-- codex-workflow-budget:end -->

<!-- codex-project-concurrency:begin -->
## Project concurrency preference
The user requested higher project concurrency on 2026-10-30. Use a workflow concurrency limit of 8 for this project; workflow 01b97d67-a4fe-4c04-a49c-c65ea9f204b0 is configured to 8. Project Codex configuration sets agents.max_threads = 8. Actual host-provided capacity, dependencies and writer conflicts still govern dispatch. This changes concurrency only; preserve all validation budgets, consumption, source artifacts and authorship.
<!-- codex-project-concurrency:end -->

## MNI authoring

Design MNI APIs, macro DSLs, examples and project templates for Kotlin first. Java interoperability
means Java can call the emitted JVM methods; Java authoring convenience is not a design requirement.
Implement built-in MNI providers and annotations in Kotlin. Preserve the JVM names and static entry
points used by MCFPP registration when migrating an existing provider.

Expose every MNI macro through an explicit MCFPP `func ... = Provider.method` declaration, using the
same syntax as native MNI functions. `injectedBy` does not expose macros. Keep annotation signatures
as type lists (`normalParams`, `returnType`); infer parameter modes from JVM wrappers.

