# Eneik Product Factory Backlog

This backlog defines the next production tasks after Task 1. Task 1, Project Factory, is implemented in the application code: creating a project now provisions a local isolated workspace, stores GitHub/Linear provisioning status, exposes factory state through the Project API, and creates seven project-scoped Jules accounts.

Every task below must follow EneikManagement principles, Lean management, JTBD, Definition of Done, Six Sigma, and Theory of Constraints. Client wishlist text is never treated as an implementation task. The Technical Lead role converts business-necessary wishes into precise tasks only when they remove a real project constraint.

## Task 2. GitHub Access Layer

Objective: turn GitHub repository provisioning into a complete access-control layer for each client project.

Business reason: a client project must be isolated. No project may see another project's repository, worktree, pull requests, secrets, tasks, or delivery state.

Trigger: a new project is created or a GitHub repository already exists and must be attached to the project.

Inputs:
- Project ID, slug, name, and repository name.
- GitHub organization or owner.
- GitHub token or GitHub App installation credentials.
- List of seven Jules accounts or integration identities that need access.

Outputs:
- Repository access manifest stored in the project record.
- GitHub collaborators or team permissions applied.
- Default branch protection configured.
- Required status checks linked to the CI workflow.
- Audit event for every permission mutation.

Functional requirements:
- Support creating a new private repository and attaching an existing repository.
- Store repository URL, repository ID, default branch, visibility, and access status.
- Grant only the active project identities access to the active repository.
- Remove or suspend access when the project is accepted or archived.
- Detect permission drift by comparing expected collaborators with actual collaborators.
- Never expose tokens in API responses, logs, task payloads, or dashboard text.

Definition of Done:
- A project has a GitHub access status visible in the dashboard.
- Seven execution identities have the expected repository access.
- Branch protection prevents direct pushes to the default branch.
- CI is required before merge.
- Permission drift is detected and shown as a blocker.

Verification:
- Integration test with a mocked GitHub API for create, invite, branch protection, and drift detection.
- Manual smoke test with real credentials in a disposable organization.
- Negative test proving one project cannot access another project's repository metadata.

## Task 3. Real Jules Dispatch

Objective: connect queued business tasks to Google Jules so the seven Jules accounts become real execution capacity, not only local account records.

Business reason: the system must convert client wishes into completed product changes, not merely display tasks.

Trigger: a task reaches queued status after Technical Lead orchestration.

Inputs:
- Task ID, role tag, task specification, repository URL, branch policy, and project context.
- Jules credentials or browser/API dispatch bridge.
- Account lease availability.

Outputs:
- Jules session ID or external task URL stored on the local task.
- Dispatch status and last synchronization timestamp.
- Execution branch name and PR URL when available.

Functional requirements:
- Dispatch only tasks whose project is active.
- Select an idle account, then bind that account to the task through a lease.
- Pass the exact Technical Lead task spec, repository URL, expected branch naming convention, and Definition of Done.
- Prevent duplicate dispatch for the same local task.
- Retry transient dispatch errors with bounded backoff.
- Store permanent failures as actionable dashboard blockers.

Definition of Done:
- A queued task can be sent to Jules and becomes in_progress locally.
- The dashboard shows which Jules account owns it.
- Dispatch failure reasons are human-readable and actionable.
- Duplicate dispatch cannot create duplicate external sessions.

Verification:
- Mocked Jules dispatch tests.
- Lease contention test with seven simultaneous tasks.
- Manual end-to-end test in a disposable repository.

## Task 4. Linear Sync

Objective: make Linear the authoritative planning board for generated business tasks while keeping Eneik Production System as the command center.

Business reason: the client and operator need transparent production state, and tasks must not disappear inside internal queues.

Trigger: project creation, task creation, task status change, PR creation, review, done, failed, or project acceptance.

Inputs:
- Project ID and Linear project ID.
- Role-tagged task spec.
- Local task status.
- PR and CI metadata.

Outputs:
- Linear project created or attached.
- Linear issues created for each generated task.
- Bidirectional status synchronization.
- Linear issue URL stored in each local task.

Functional requirements:
- Create Linear issues only from Technical Lead generated tasks.
- Never create Linear issues directly from raw wishlist items.
- Map local statuses to Linear workflow states.
- Preserve role tag, JTBD, DoD, TOC constraint, and Six Sigma quality target in the issue body.
- Detect and reconcile external Linear status changes.
- Show sync errors as dashboard blockers.

Definition of Done:
- Every generated task has a Linear issue.
- Closing or failing a task updates Linear.
- Linear issue comments can be imported as role advice or client wishlist, depending on source.

Verification:
- GraphQL mocked tests for project and issue mutations.
- Status mapping contract test.
- Manual test with a real Linear team and disposable project.

## Task 5. Technical Lead Task Compiler

Objective: upgrade the Technical Lead from simple keyword routing to a deterministic task compiler that turns ambiguous wishes into precise business tasks.

Business reason: the customer can write anything, but the production system must create only clear, valuable, reviewable tasks.

Trigger: open wishlist items exist and orchestration is requested or scheduled.

Inputs:
- Client wishlist text.
- Project context.
- Existing tasks, blockers, PRs, and role advice.
- Current bottleneck and acceptance criteria.

Outputs:
- One or more precise tasks.
- Or an explicit decision to ignore, defer, or ask for clarification.
- A reasoning trace that names the business necessity.

Functional requirements:
- Apply JTBD before implementation details.
- Identify the current TOC constraint.
- Prefer the smallest Lean increment that can move the project closer to acceptance.
- Generate role-specific tasks with no cross-role ambiguity.
- Include objective Definition of Done.
- Include measurable Six Sigma defect criteria.
- Reject wishes that do not remove a business constraint.

Definition of Done:
- Task specs are readable without hidden context.
- Each task has exactly one primary role and one measurable business outcome.
- The dashboard can show why the task exists.

Verification:
- Golden-master tests for representative client wishes.
- Regression tests for vague, duplicate, low-value, and conflicting wishes.
- Human review checklist for generated task quality.

## Task 6. Role Advice Loop

Objective: let roles produce advisory wishlist items after completing work, while the Technical Lead remains the only task creator.

Business reason: specialists see constraints while working, but uncontrolled task creation creates waste and destroys focus.

Trigger: a task moves to done, failed, or review-rejected.

Inputs:
- Completed task output.
- Role notes.
- Test failures, PR comments, UX findings, or architecture risks.

Outputs:
- Role advice wishlist item.
- Business necessity score.
- Technical Lead decision to convert, defer, ignore, or merge with existing work.

Functional requirements:
- Role advice is stored separately from client wishes.
- Role advice cannot create tasks directly.
- Technical Lead considers advice only when no higher-value business task blocks acceptance.
- Advice must name a risk, opportunity, or constraint.
- Duplicate advice must be merged.

Definition of Done:
- Every completed task can produce optional advice.
- Advice appears in the dashboard with source role and status.
- The orchestrator can convert advice into tasks only after business-necessity evaluation.

Verification:
- Tests for advice creation, duplicate merging, and conversion.
- Dashboard smoke test showing source role and decision status.

## Task 7. Task Quality Gate

Objective: block weak tasks before they reach Jules.

Business reason: bad tasks consume execution capacity and produce bad product outcomes.

Trigger: a task is generated by the Technical Lead.

Inputs:
- Task title and body.
- Role tag.
- JTBD, DoD, Lean increment, TOC constraint, and Six Sigma criteria.
- Project repository and Linear state.

Outputs:
- Quality score.
- Pass/fail decision.
- Specific remediation feedback.

Functional requirements:
- Validate that every task has a single role owner.
- Validate that the task is testable.
- Validate that the task names the business outcome.
- Validate that the task removes or reduces a current constraint.
- Validate that the task is small enough for one Jules execution slice.
- Reject tasks with vague verbs such as "improve" without measurable criteria.

Definition of Done:
- No task can be dispatched without passing the quality gate.
- Failed tasks remain visible with exact reasons.
- The Technical Lead can regenerate a corrected task.

Verification:
- Unit tests for task lint rules.
- Integration test proving failed tasks are not dispatched.
- Fixture set of strong and weak tasks.

## Task 8. Jules Execution Monitor

Objective: continuously synchronize Jules execution progress back into the Eneik dashboard.

Business reason: the operator needs full visibility into who is working, what is blocked, and whether the project is moving toward acceptance.

Trigger: scheduled polling, webhook callback, manual refresh, or task status event.

Inputs:
- Jules session IDs.
- Local task and claim records.
- Repository branch and PR metadata.

Outputs:
- Updated task status.
- Heartbeat timestamps.
- Blocker records.
- Expired lease recovery actions.

Functional requirements:
- Detect idle, active, blocked, completed, and failed Jules sessions.
- Renew active leases.
- Release stale leases after timeout.
- Requeue recoverable tasks.
- Mark irrecoverable failures with clear reasons.
- Keep a history of state transitions.

Definition of Done:
- Dashboard reflects actual Jules progress within an acceptable polling interval.
- Stale tasks do not remain stuck forever.
- Recovery behavior is deterministic and auditable.

Verification:
- Lease watchdog integration tests.
- Mocked Jules status polling tests.
- Time-based tests for stale lease expiration.

## Task 9. PR Review Pipeline

Objective: turn external code changes into a controlled review and merge pipeline.

Business reason: customer-visible quality depends on merging only changes that satisfy the task and do not regress the product.

Trigger: Jules opens or updates a pull request.

Inputs:
- PR URL, branch, diff summary, CI status, Linear issue, and local task.

Outputs:
- Review status.
- Merge decision.
- Delivery note.
- Task transition to review, done, or failed.

Functional requirements:
- Link every PR to exactly one local task.
- Verify CI status before review approval.
- Require DoD checklist completion.
- Summarize customer value in non-technical language.
- Reject PRs that alter unrelated scope.
- Record review findings.

Definition of Done:
- A PR cannot mark a task done unless CI passes and DoD is satisfied.
- The dashboard shows PR status and review outcome.
- Rejected PRs generate precise remediation tasks or comments.

Verification:
- GitHub webhook tests.
- CI status mapping tests.
- Diff scope smoke tests.

## Task 10. Design Excellence Gate

Objective: add a design review gate for all client-visible UI work.

Business reason: the system must produce products customers love enough to accept and pay for, not merely functional screens.

Trigger: a task touches frontend, copy, layout, visual hierarchy, interaction, or client-facing flow.

Inputs:
- Screenshot or browser capture.
- Task DoD.
- Brand/project context.
- Accessibility and responsiveness rules.

Outputs:
- Design review score.
- Required fixes.
- Approved screenshot evidence.

Functional requirements:
- Capture desktop and mobile screenshots.
- Check visual hierarchy, spacing, text fit, responsiveness, accessibility, and domain fit.
- Require real product UI, not placeholder landing pages, unless the client requested one.
- Prevent incoherent overlap, broken typography, and unclear dashboard states.
- Store review evidence with the task.

Definition of Done:
- UI tasks include visual proof.
- The design gate can fail a task before client delivery.
- Client-visible changes have concise value notes.

Verification:
- Playwright screenshot tests.
- Accessibility smoke checks.
- Manual visual approval checklist.

## Task 11. Backend Contract Gate

Objective: protect API, persistence, and integration contracts before tasks are accepted.

Business reason: backend regressions silently break the production mechanism and destroy trust.

Trigger: a task changes controllers, DTOs, database migrations, repositories, service contracts, or external integrations.

Inputs:
- OpenAPI contract.
- Database migrations.
- Test results.
- DTO/schema diff.

Outputs:
- Contract validation result.
- Migration safety report.
- API compatibility note.

Functional requirements:
- Detect breaking API changes.
- Require Flyway migrations for schema changes.
- Verify migration order and repeatability.
- Validate DTO compatibility with frontend types.
- Validate external API errors are safe and visible.

Definition of Done:
- Backend task cannot reach done with failing contract checks.
- Schema changes are represented by migrations.
- API consumers have updated types or compatibility shims.

Verification:
- OpenAPI diff tests.
- Flyway migration tests on a clean database.
- Frontend type/build verification.

## Task 12. Project Command Dashboard v2

Objective: redesign the dashboard so the operator immediately understands project value, production state, blockers, and next action.

Business reason: if the operator cannot understand the dashboard, the system cannot be trusted to run production.

Trigger: after Project Factory, Linear sync, Jules dispatch, and monitor primitives exist.

Inputs:
- Project factory state.
- Queue, pipeline, agents, blockers, PRs, Linear issues, wishlist, and acceptance criteria.

Outputs:
- Operator dashboard with clear project selector.
- One active project view.
- Status bands for factory, planning, execution, review, delivery, and acceptance.
- Blocker-first action panel.

Functional requirements:
- Show exactly one active project context at a time.
- Make cross-project isolation visible.
- Show the seven Jules accounts and their current role/task.
- Show wishlist separately from tasks.
- Show Technical Lead decisions.
- Show bottleneck and recommended next action.
- Avoid decorative marketing layout; prioritize dense operational clarity.

Definition of Done:
- A new user can answer: what project is active, what is being built, who is working, what is blocked, what is ready for review, and what action is next.
- Dashboard remains readable on desktop and mobile.
- No critical status is hidden behind developer logs.

Verification:
- Playwright desktop and mobile screenshots.
- Usability checklist.
- API fixture-driven rendering tests.

## Task 13. Client Delivery View

Objective: give the client a simplified acceptance-oriented view separate from the operator command dashboard.

Business reason: the client should see business outcomes, progress, proofs, and acceptance controls without internal operational noise.

Trigger: project has at least one done task, delivery note, preview URL, or acceptance checkpoint.

Inputs:
- Completed tasks.
- Screenshots, preview URLs, PR summaries, release notes, and known limitations.

Outputs:
- Client delivery page.
- Acceptance checklist.
- "Project accepted" stop button.
- Feedback-to-wishlist input.

Functional requirements:
- Show what changed in client language.
- Show evidence: screenshots, links, tests, or demos.
- Let client add wishes without creating tasks directly.
- Make acceptance final and visible.
- Stop new orchestration after acceptance.

Definition of Done:
- Client can understand delivered value without reading internal tasks.
- Client can accept the project.
- Acceptance stops production and records timestamp.

Verification:
- End-to-end test for feedback and acceptance.
- UI screenshot tests.
- State transition test after acceptance.

## Task 14. Acceptance and Stop Protocol

Objective: formalize project completion so production stops cleanly and all systems agree.

Business reason: unlimited production creates waste. Payment and closure require a clear stop condition.

Trigger: client clicks "Project accepted" or an authorized operator closes the project.

Inputs:
- Project ID.
- Delivery state.
- Open tasks, PRs, Linear issues, Jules sessions, and wishlist items.

Outputs:
- Accepted project state.
- Locked wishlist intake.
- Released Jules leases.
- Closed or archived Linear/GitHub artifacts according to policy.
- Final delivery manifest.

Functional requirements:
- Block new wishlist conversion after acceptance.
- Release or terminate active Jules work safely.
- Mark unfinished tasks as stopped, deferred, or accepted exception.
- Archive production logs and reports.
- Generate final manifest with repo, Linear project, PRs, delivered tasks, tests, and acceptance timestamp.

Definition of Done:
- Accepted projects cannot generate new tasks.
- All active execution leases are released or explicitly marked stopped.
- The final manifest is visible to operator and client.
- Project state is consistent across Eneik, GitHub, Linear, and Jules.

Verification:
- End-to-end acceptance test.
- Consistency test across local records.
- Manual dry run on a disposable project with active queued and in-progress tasks.
