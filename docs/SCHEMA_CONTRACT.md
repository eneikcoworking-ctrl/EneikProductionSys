# SCHEMA CONTRACT: TABLE OWNERSHIP & ACCESS CONTROL

To ensure parallel development without agent interference, this contract defines which tasks (and their assigned agents) have read/write permissions for specific tables.

## 1. OWNERSHIP MATRIX

| Table | Owner (Task ID) | Write Access | Read Access |
|-------|-----------------|--------------|-------------|
| **accounts** | Task 1 | Task 2, Task 3 | All |
| **roles** | Task 1 | Initial Migration only | All |
| **tasks** | Task 1 | Task 2, Task 3, Task 4, Task 5 | All |
| **claims** | Task 1 | Task 2, Task 3 | All |

## 2. AGENT-TASK MAPPING PER TABLE

- **Task 1 (TAG-01, TAG-08)**: SCHEMA CREATOR. Defines all tables, enums, and base entities.
- **Task 2 (TAG-09)**: DECOMPOSITION. Only `INSERT` into `tasks`. Sets `status = 'queued'`.
- **Task 3 (TAG-02)**: LIFECYCLE/CLAIM. `INSERT` into `claims`, `UPDATE` `tasks.status`, `UPDATE` `accounts.status`.
- **Task 4 (TAG-11, TAG-03)**: DASHBOARD. Strictly `SELECT` only. No writes.
- **Task 5 (TAG-05)**: LINEAR SYNC. `UPDATE` `tasks.linear_issue_id`, `UPDATE` `tasks.status` (via Webhook). **Note:** Linear sync NEVER touches `claims`. Sync works via direct polling of the `tasks` table.
- **Task 6 (TAG-04)**: ANALYTICS. Strictly `SELECT` only.
- **Task 7 (TAG-00)**: AUDIT. Strictly `SELECT` only.

## 3. CORE ENUMS (Shared)

- **AccountStatus**: `idle`, `busy`, `offline`
- **TaskStatus**: `queued`, `claimed`, `in_progress`, `review`, `done`, `failed`
- **ClaimResultStatus**: `done`, `failed`, `expired`

## 4. CONCURRENCY PROTECTION (IMPORTANT)

The "one active claim per task" constraint **must be enforced at the application level** (Java Service/Controller) for compatibility with the H2 test environment.

In a production PostgreSQL environment, this should be backed by a partial unique index:
```sql
CREATE UNIQUE INDEX uq_claims_active_task ON claims(task_id) WHERE released_at IS NULL;
```
However, to ensure `mvn test` passes reliably on H2, this index is **omitted** from the core migration.
