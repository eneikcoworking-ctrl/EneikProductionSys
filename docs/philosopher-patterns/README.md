# Philosopher Patterns RAG Corpus

Generated from `BARCAN-TAG-*.md`: 13 role files, 78 philosophers, at least 20 unique personal programming patterns per philosopher.

## Files

- [`00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`](00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md) - shared world-class practices that fit more than 5 philosophers.
- [`01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`](01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md) - anti-conflict rules extracted from the session's PR repair work.
- [`02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md`](02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md) - deterministic scoring, threshold and QA contract for RAG.
- [`PHILOSOPHER_INDEX.md`](PHILOSOPHER_INDEX.md) - navigation table for all philosophers.
- [`philosopher_patterns_index.json`](philosopher_patterns_index.json) - machine-readable RAG index.
- [`QA_REPORT.md`](QA_REPORT.md) - exact count and uniqueness checks.
- [`philosophers/`](philosophers/) - one file per philosopher.

## Hard Invariant

Each philosopher file has at least 20 personal patterns. If a concrete pattern becomes useful for more than 5 philosophers, move it to the common file and replace it with a philosopher-specific micro-pattern.

## Retrieval Order

1. Retrieve common ACP patterns.
2. Retrieve the mathematical assignment model when explanation or audit is needed.
3. Retrieve the one philosopher file matching the active BARCAN role.
4. Retrieve the parallel-development charter for merge, PR, queue, schema or route work.
