# Parallel Development Conflict Prevention Charter

This file is the shared anti-conflict layer extracted from the session work on stuck PRs, stale branches, duplicate routes, CI reconciliation and merge ordering.

| ID | Rule | Requirement |
|---|---|---|
| `CPF-001` | Single owner for every mutable surface | Every shared file, contract, enum, migration lane and generated artifact has one owner. |
| `CPF-002` | Contract before implementation | API, event, schema and RAG contracts are reviewed before parallel implementation starts. |
| `CPF-003` | Generated files are read-only | Change the generator or source data, then regenerate. |
| `CPF-004` | Append-only by default | Prefer new extension files or registry rows over edits to shared central files. |
| `CPF-005` | Small vertical PRs | One PR covers one behavior slice, not feature plus refactor plus formatting. |
| `CPF-006` | Merge queue over direct merge | Every PR is rebuilt on current main before landing. |
| `CPF-007` | Serialized migrations | Database migrations, shared enums and global schemas pass through a single ordered lane. |
| `CPF-008` | Refactor freeze lane | Broad moves, renames and formatting run in their own window. |
| `CPF-009` | Conflict forecast | Task and PR describe touched paths, owners, contracts and expected integration points. |
| `CPF-010` | Semantic conflict checks | After merge, affected contract, schema and smoke tests must run. |
| `CPF-011` | Feature flag isolation | Incomplete parallel work cannot affect shared runtime without a flag. |
| `CPF-012` | Evidence after conflict resolution | Manual conflict fixes require targeted tests or contract checks. |

## Required Agent Sequence

1. Before coding, declare `touched_paths`, owners, contracts and likely integration points.
2. During coding, do not edit generated files manually, root `.gitignore`, shared migrations or shared enum/constants without the owner lane.
3. Before merge, rebuild on current `main`, run affected contract/schema/smoke tests and record the evidence.
4. After a manual conflict resolution, add or run a targeted check proving that behavior, not only text, still matches.
