# Philosopher Patterns RAG Corpus

Generated from `BARCAN-TAG-*.md`: 13 role files, 78 philosophers, 10 unique personal programming patterns per philosopher.

## Files

- [`00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`](00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md) - shared world-class practices that fit more than five philosophers.
- [`01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md`](01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md) - anti-conflict rules extracted from the session's PR repair work.
- [`PHILOSOPHER_INDEX.md`](PHILOSOPHER_INDEX.md) - navigation table for all philosophers.
- [`philosopher_patterns_index.json`](philosopher_patterns_index.json) - machine-readable RAG index.
- [`QA_REPORT.md`](QA_REPORT.md) - exact count and uniqueness checks.
- [`philosophers/`](philosophers/) - one file per philosopher.

## Hard Invariant

Each philosopher file has exactly 10 personal patterns. If a pattern becomes useful for more than five philosophers, move it to the common file and replace it with a philosopher-specific micro-pattern.
