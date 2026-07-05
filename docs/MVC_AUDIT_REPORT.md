# MVC Architecture Audit Report

## 1. CONTROLLER LAYER

- **File:Line**: `src/main/java/com/eneik/production/controllers/accounts/AccountController.java:31`
- **Code**: `private final AccountRepository accountRepository;`
- **Rule Violated**: "Forbidden: прямой доступ к Repository, минуя Service"
- **Severity**: Critical
- **Details**: The controller directly interacts with the database for all CRUD operations on accounts.

- **File:Line**: `src/main/java/com/eneik/production/controllers/dashboard/DashboardController.java:20-22`
- **Code**:
```java
    private final AccountRepository accountRepository;
    private final ClaimRepository claimRepository;
    private final TaskRepository taskRepository;
```
- **Rule Violated**: "Forbidden: прямой доступ к Repository, минуя Service"
- **Severity**: Critical
- **Details**: Multiple repositories are injected and used to aggregate dashboard data instead of calling a Domain Service.

- **File:Line**: `src/main/java/com/eneik/production/controllers/InternalTaskController.java:27-29`
- **Code**:
```java
    private final TaskRepository taskRepository;
    private final LinearIssueMetadataRepository metadataRepository;
    private final ClaimRepository claimRepository;
```
- **Rule Violated**: "Forbidden: прямой доступ к Repository, минуя Service"
- **Severity**: Critical
- **Details**: Direct repository access for internal synchronization logic.

- **File:Line**: `src/main/java/com/eneik/production/controllers/InternalTaskController.java:95-108`
- **Code**:
```java
        jdbcTemplate.update(
                """
                MERGE INTO linear_issue_metadata (task_id, linear_issue_id, blockers, dod_text, pr_url, last_synced_at)
                ...
                """,
                ...
        );
```
- **Rule Violated**: "Forbidden: любой if/else, кодирующий бизнес-смысл... Obligatory: вызывает РОВНО ОДИН метод Model-слоя"
- **Severity**: Critical
- **Details**: Business logic for merging metadata is implemented directly in the controller using `jdbcTemplate`.

---

## 2. CONFIG-AS-BUSINESS-LOGIC

- **File:Line**: `src/main/java/com/eneik/production/services/jules/JulesDispatchService.java:33`
- **Code**: `@Value("${jules.source-prefix:sources/github/eneikcoworking-ctrl/}") String sourcePrefix`
- **Rule Violated**: "Forbidden: хардкод значений, влияющих на поведение... спрятанное в конфиг-файле вместо Model-слоя"
- **Severity**: Critical
- **Details**: The path construction logic for repositories is driven by a configuration string that encodes business structure.

- **File:Line**: `src/main/java/com/eneik/production/services/ProjectFlowService.java:60`
- **Code**: `@Value("${github.org:eneikcoworking-ctrl}") String githubOrganization`
- **Rule Violated**: "Forbidden: хардкод значений, влияющих на поведение... спрятанное в конфиг-файле вместо Model-слоя"
- **Severity**: Critical
- **Details**: The organization name used to build URLs is a business decision residing in config/Service field instead of a proper Model entity or Domain Service.

- **Confirmed Violation 1**: `ProjectFlowService.orchestrate()` calling dispatch with `accountId=null`.
- **Status**: **Still present**.
- **Code**: `JulesDispatchResult dispatch = julesDispatchService.dispatch(savedTask);` (Line 239) calls `dispatchInternal(task, null)`.
- **Standard Reference**: "бизнес-решение 'какой аккаунт использовать', спрятанное в конфиг-файле вместо Model-слоя".

---

## 3. MODEL CONSULTATION GAPS

- **Confirmed Violation 2**: `JulesDispatchService` must call `RoleCapabilityLoader`.
- **Status**: **Still present**.
- **File:Line**: `src/main/java/com/eneik/production/services/jules/JulesDispatchService.java:66`
- **Code**: `String roleContext = "Role: " + task.getRole().getTag() + "\n" + task.getRole().getDescription();`
- **Severity**: Critical
- **Details**: The service uses raw entity fields instead of consulting `RoleCapabilityLoader.loadRules()` to get the full role charter/capabilities before forming the prompt.

---

## 4. VIEW LAYER

- **File:Line**: `frontend/src/dashboard/AdminDashboard.svelte:53-59`
- **Code**:
```javascript
  function collaboratorStatusLabel(statusValue: string) {
    if (statusValue === 'invitation_sent') return 'Invitation sent';
    if (statusValue === 'already_has_access') return 'Collaborator';
    ...
  }
```
- **Rule Violated**: "Forbidden решать 'готов ли проект к принятию' через собственную логику на сырых булевых полях — это должно быть полем, которое backend уже посчитал и отдал"
- **Severity**: Cosmetic/Medium
- **Details**: Presentation logic duplicates business interpretations of status codes.

- **File:Line**: `frontend/src/dashboard/CommandDashboardV2.svelte:36`
- **Code**: `{dashboardData.acceptanceReadiness.readiness === 'ready' ? 'border-green-500' : ...}`
- **Rule Violated**: "Любое 'включена ли кнопка' — управляется полем, которое вернул backend, не клиентской бизнес-логикой."
- **Severity**: Cosmetic
- **Details**: While simple, the interpretation of 'ready' to determine visual state (green border) should ideally be pre-calculated as a 'tone' or 'statusClass' by the backend.

---

## 5. SCHEDULED JOBS

- **File:Line**: `src/main/java/com/eneik/production/services/LeaseWatchdogService.java:43-62`
- **Code**:
```java
    @Scheduled(fixedRate = 60000)
    public void reapExpiredLeases() { ... }
```
- **Rule Violated**: "Obligatory: вызывать ТЕ ЖЕ методы Domain Service, что вызвал бы Controller — Forbidden дублировать или обходить бизнес-правило 'для эффективности'"
- **Severity**: Critical
- **Details**: `LeaseWatchdogService` implements the expiration logic (state transitions for Claim, Task, and Account) inside the scheduled method instead of calling a method in `ClaimService`.

- **File:Line**: `src/main/java/com/eneik/production/services/jules/JulesDispatchService.java:166-179`
- **Code**:
```java
    @Scheduled(fixedRateString = "${jules.detect-stuck-rate-ms:60000}")
    public void detectStuck() { ... }
```
- **Rule Violated**: "Obligatory: вызывать ТЕ ЖЕ методы Domain Service... Forbidden дублировать или обходить бизнес-правило"
- **Severity**: Critical
- **Details**: `detectStuck` implements session status monitoring and state transitions directly in the scheduled method.

- **File:Line**: `src/main/java/com/eneik/production/services/BottleneckAwarePriorityService.java:44-77`
- **Code**:
```java
    @Scheduled(fixedRate = 300000)
    public void refreshQueuedTasksPriority() { ... }
```
- **Rule Violated**: "Obligatory: вызывать ТЕ ЖЕ методы Domain Service... Forbidden дублировать или обходить бизнес-правило"
- **Severity**: Critical
- **Details**: Logic for deciding and updating task priority is embedded in the scheduled task.

---

## 6. REPOSITORY LAYER

- **File:Line**: `src/main/java/com/eneik/production/repositories/TaskRepository.java:29-33`
- **Code**:
```sql
    @Query(value = "SELECT t.* FROM tasks t " +
            "JOIN projects p ON t.project_id = p.id " +
            "WHERE t.status = 'queued' AND t.tag IN (:capableTags) AND p.status = 'active' " +
            "ORDER BY t.priority DESC, t.created_at ASC " +
            "LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
```
- **Rule Violated**: "Forbidden содержать условия, кодирующие бизнес-решение (например Forbidden: repository-метод, который решает 'достаточно ли этот PR безопасен для merge')"
- **Severity**: Medium
- **Details**: The repository decides what is "the next task" by checking `p.status = 'active'` and applying priority/date ordering. These are business rules that should be passed as parameters or handled by the Domain Service logic.
