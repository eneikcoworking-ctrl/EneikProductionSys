package com.eneik.production.services.judgment;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Asks a delivered task the one question nothing asked it: does the merged diff satisfy the statement this
 * task said its completion could be tested against?
 *
 * Why it exists. Measured on 2026-08-23, project test-fiftieth: twenty-six tasks, thirteen merged pull
 * requests, and zero deliveries verified - `applicableChecksByStage.IMPLEMENTATION_RESULT` was 0 for all
 * twenty-six. The gates were not weak, they were unreachable: `GateOrchestrator.runQualityGate` stands on
 * one of the five paths that write TaskStatus.done, and covers five of the thirteen roles. Adding gates for
 * the other eight would have been eight more inspections of finished output. A task's own acceptance
 * criteria are a better instrument than any per-role gate, because they are what the client asked for
 * rather than what a role generally owes, and every task already carries them.
 *
 * Level. This rules on DELIVERY only. JudgmentAgentClient.judge() carries a system prompt that rules on the
 * FACTORY and says so - "DELIVERY: whether a client's brief has been carried to a product. Not your
 * subject." Reusing it here would merge two of the three contexts the plan keeps apart, so this uses
 * judgeAsText with its own instruction and its own reply contract.
 *
 * Refusal is not this service's job. It records a verdict and files a refutation when one is refuted; it
 * never blocks a transition. Refusing on ignorance turns a safety net into a new way to strand tasks - the
 * reasoning already written into AutoMergeService on 2026-08-18 - and eight of thirteen roles would be
 * refused on ignorance from the first tick.
 */
@Service
public class DeliveredWorkJudgmentService {

    private static final Logger log = LoggerFactory.getLogger(DeliveredWorkJudgmentService.class);

    static final String VERDICT_KEY = "acceptance_verdict";
    static final String VERDICT_REASON_KEY = "acceptance_verdict_reason";
    static final String VERDICT_AT_KEY = "acceptance_verdict_at";

    /** Recorded, never inferred: a task marked done that produced no diff was not thereby verified. */
    static final String NOT_JUDGED_NO_DIFF = "NOT_JUDGED_NO_DIFF";
    static final String SATISFIED = "SATISFIED";
    static final String REFUTED = "REFUTED";
    static final String UNDECIDABLE = "UNDECIDABLE";

    private static final String SYSTEM_INSTRUCTION = """
            You are reading one task of an autonomous software factory that has been marked done, together \
            with the statement its completion was supposed to be testable against, and the diff that was \
            merged for it.

            Keep three levels apart and rule on exactly one:
              - FACTORY: the orchestrator's own code and behaviour. Not your subject.
              - DELIVERY: whether this task's acceptance criteria are satisfied by this diff. This, only this.
              - PRODUCT: whether a running client instance is healthy. Not your subject.

            Answer with a first line that is exactly one of these words and nothing else:
              SATISFIED   - every criterion is met by something you can point to in this diff.
              REFUTED     - at least one criterion is not met by this diff.
              UNDECIDABLE - the criteria cannot be tested against a diff at all, because they describe a \
            runtime behaviour or an artefact this diff does not contain.

            Then a blank line, then at most four lines of reason. For REFUTED, name the criterion that fails \
            and what the diff does instead. For SATISFIED, name where each criterion is met.

            Do not answer SATISFIED because the diff looks reasonable or the work looks competent. A \
            criterion you cannot point to in the diff is not satisfied, and saying so is the whole of your \
            usefulness here.
            """;

    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final JudgmentAgentClient judgmentAgentClient;
    private final WishlistRepository wishlistRepository;

    @Value("${delivered-work-judgment.enabled:true}")
    private boolean enabled;

    /**
     * One per tick, deliberately. The orchestration tick is @Scheduled(fixedRate 60s) on a single thread and
     * this call is synchronous, with the sidecar's own timeout at 300s - two per project per tick could hold
     * the whole loop for ten minutes on a host the operator already measures as short of memory. One call
     * clears twenty-six closed tasks in about twenty-six minutes, which is faster than they arrived.
     */
    @Value("${delivered-work-judgment.max-per-cycle:1}")
    private int maxPerCycle;

    /**
     * 2026-08-23 (F11). The sidecar hands the prompt to a spawned process as a single argument, and a
     * single argument has a hard kernel limit. At 60000 characters of diff - Cyrillic content costing two
     * bytes each - task d94c75cb crossed it and killed the sidecar with `spawn E2BIG` three times, three
     * times out of three attempts. That sidecar is shared with the factory's own judgment layer, so an
     * unbounded value from this caller took down an instrument that is not this caller's to break.
     */
    @Value("${delivered-work-judgment.diff-char-limit:12000}")
    private int diffCharLimit;

    /**
     * How many times a task may return silence before the silence is recorded as a fact about the task
     * rather than about the instrument. JudgmentAgentClient's own javadoc names the failure this prevents:
     * treating "the endpoint could not answer" and "this input cannot be ruled on" as one thing makes the
     * offending row an absorbing state at the head of the queue and stops all judgment. This service made
     * exactly that mistake - every null was read as an unavailable sidecar, so the input that crashes it
     * was retried forever.
     */
    @Value("${delivered-work-judgment.max-consecutive-silences:2}")
    private int maxConsecutiveSilences;

    public DeliveredWorkJudgmentService(TaskRepository taskRepository,
                                        JulesSessionRepository julesSessionRepository,
                                        GitHubPullRequestService gitHubPullRequestService,
                                        JudgmentAgentClient judgmentAgentClient,
                                        WishlistRepository wishlistRepository) {
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.judgmentAgentClient = judgmentAgentClient;
        this.wishlistRepository = wishlistRepository;
    }

    public void judgeDeliveredWork(ProjectEntity project) {
        if (!enabled) {
            return;
        }
        List<TaskEntity> closed = taskRepository
                .findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.done);
        List<TaskEntity> judgeable = closed.stream()
                .filter(task -> task.getAcceptanceCriteria() != null)
                .toList();

        // 2026-08-23 (D2). State the denominator. This service's population is closed tasks CARRYING a
        // criterion, not closed tasks - measured 15 of 21 on test-fiftieth, because thirteen of the
        // fourteen places that build a task set none until today. Reporting verdicts against the larger
        // number would repeat the substitution this whole layer exists to remove: the difference is a debt,
        // not a zero, and it has to be visible to be paid.
        long judged = judgeable.stream().filter(task -> verdictOf(task) != null).count();
        log.info("[DELIVERY-JUDGMENT] project {}: {} of {} closed tasks carry a criterion; {} of those judged",
                project.getId(), judgeable.size(), closed.size(), judged);

        List<TaskEntity> awaiting = judgeable.stream()
                .filter(task -> verdictOf(task) == null)
                .limit(Math.max(1, maxPerCycle))
                .toList();

        for (TaskEntity task : awaiting) {
            try {
                judgeOne(project, task);
            } catch (Exception e) {
                log.warn("[DELIVERY-JUDGMENT] task {} could not be judged: {}", task.getId(), e.getMessage());
            }
        }
    }

    private void judgeOne(ProjectEntity project, TaskEntity task) {
        // 2026-08-23 (D3). Decided before any diff is fetched and before a single token is spent. Until
        // today englishMetadata replaced the client's stated criterion with three process statements
        // whenever it was written in the client's own language, which on this project was every one of
        // them. Those statements - the change passes, the diff is clean, a blocker is recorded - cannot be
        // false of a product that does nothing the client asked for, so a SATISFIED against them is a
        // verdict about the work's tidiness and says nothing about delivery. Recording that as UNDECIDABLE
        // is the honest reading; recording it as passed is what this layer was built to stop.
        if (TechnicalLeadCompiler.PROCESS_ACCEPTANCE_CRITERIA.trim()
                .equals(task.getAcceptanceCriteria().trim())) {
            record(task, UNDECIDABLE, "this task carries only the compiler's process criteria and no claim "
                    + "about the product, because the criterion the client stated was substituted before "
                    + "dispatch; delivery cannot be tested against statements that no delivery can falsify");
            return;
        }

        Optional<String> prUrl = julesSessionRepository.findByTaskId(task.getId()).stream()
                .map(JulesSessionEntity::getPrUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst();
        if (prUrl.isEmpty()) {
            record(task, NOT_JUDGED_NO_DIFF, "no pull request is recorded against this task, so its "
                    + "acceptance criteria were never tested against delivered code");
            return;
        }

        Integer pullNumber = gitHubPullRequestService.parsePullNumber(prUrl.get());
        Optional<String> diff = pullNumber == null
                ? Optional.empty()
                : gitHubPullRequestService.fetchDiffText(project, pullNumber);
        if (diff.isEmpty() || diff.get().isBlank()) {
            record(task, NOT_JUDGED_NO_DIFF, "the diff of " + prUrl.get() + " could not be read");
            return;
        }

        String answer = judgmentAgentClient.judgeAsText(prompt(task, prUrl.get(), diff.get()), SYSTEM_INSTRUCTION);
        if (answer == null || answer.isBlank()) {
            int silences = silenceCountOf(task) + 1;
            if (silences >= Math.max(1, maxConsecutiveSilences)) {
                record(task, UNDECIDABLE, "the judgment channel could not carry this input after "
                        + silences + " attempts; the merged diff of " + prUrl.get() + " does not fit the "
                        + "sidecar's argument limit, so this delivery cannot be tested against its criteria "
                        + "from a diff alone");
                log.warn("[DELIVERY-JUDGMENT] task {} recorded UNDECIDABLE after {} silences - the input, "
                        + "not the instrument, is what cannot be carried", task.getId(), silences);
                return;
            }
            recordSilence(task, silences);
            log.info("[DELIVERY-JUDGMENT] sidecar gave no answer for task {} (silence {} of {}); retrying",
                    task.getId(), silences, maxConsecutiveSilences);
            return;
        }

        String verdict = firstWord(answer);
        String reason = reasonOf(answer);
        if (!SATISFIED.equals(verdict) && !REFUTED.equals(verdict) && !UNDECIDABLE.equals(verdict)) {
            // The reply did not meet the declared contract, and the same input will not meet it next time.
            record(task, UNDECIDABLE, "the judgment did not answer in the declared form: " + firstLine(answer));
            return;
        }

        record(task, verdict, reason);
        log.info("[DELIVERY-JUDGMENT] task {} ({}) -> {}", task.getId(), task.getTitle(), verdict);

        if (REFUTED.equals(verdict)) {
            fileRefutation(project, task, prUrl.get(), reason);
        }
    }

    private String prompt(TaskEntity task, String prUrl, String diff) {
        boolean truncated = diff.length() > diffCharLimit;
        String bounded = truncated ? diff.substring(0, diffCharLimit) : diff;
        String warning = truncated
                ? "\n\nTHIS DIFF IS INCOMPLETE. You are seeing the first " + diffCharLimit + " characters of "
                + diff.length() + ". Do not answer SATISFIED on a partial diff: if a criterion's evidence "
                + "could lie in the part you cannot see, answer UNDECIDABLE and say which criterion it was.\n"
                : "";
        String prompt = "TASK TITLE\n" + task.getTitle()
                + "\n\nWHAT THIS TASK WAS ASKED TO DO\n"
                + cap(task.getDescription() == null ? "(no description recorded)" : task.getDescription(),
                     DESCRIPTION_CHAR_LIMIT)
                + "\n\nACCEPTANCE CRITERIA THIS TASK CARRIED\n" + cap(task.getAcceptanceCriteria(), 4_000)
                + "\n\nMERGED PULL REQUEST\n" + prUrl
                + warning
                + "\n\nDIFF\n" + bounded;

        // 2026-08-23, second pass. The first fix capped the DIFF and called the channel bounded, and the
        // sidecar went on dying: four more `spawn E2BIG` in the fifteen minutes after that deploy. What the
        // channel carries is the SUM, and the compiler writes task descriptions that are themselves
        // thousands of characters. Bounding one addend is not bounding the total - the same substitution of
        // a part for the whole that this service exists to catch in other people's work. This last cap is
        // the one that is actually true of the argument handed to spawn.
        return cap(prompt, PROMPT_CHAR_LIMIT);
    }

    private static final int DESCRIPTION_CHAR_LIMIT = 6_000;

    /**
     * The whole prompt, system instruction included, must survive being handed to a spawned process as one
     * argument. Kept well under the kernel's per-argument limit rather than near it, because the content is
     * frequently Cyrillic and every one of those characters costs two bytes where the limit counts bytes.
     */
    private static final int PROMPT_CHAR_LIMIT = 40_000;

    private String cap(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "\n[truncated]";
    }

    private void record(TaskEntity task, String verdict, String reason) {
        ObjectNode payload = task.getPayload() instanceof ObjectNode node
                ? node
                : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(VERDICT_KEY, verdict);
        payload.put(VERDICT_REASON_KEY, reason == null ? "" : reason);
        payload.put(VERDICT_AT_KEY, Instant.now().toString());
        task.setPayload(payload);
        taskRepository.save(task);
    }

    private static final String SILENCE_COUNT_KEY = "acceptance_silence_count";

    private int silenceCountOf(TaskEntity task) {
        return task.getPayload() == null ? 0 : task.getPayload().path(SILENCE_COUNT_KEY).asInt(0);
    }

    private void recordSilence(TaskEntity task, int silences) {
        ObjectNode payload = task.getPayload() instanceof ObjectNode node
                ? node
                : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(SILENCE_COUNT_KEY, silences);
        task.setPayload(payload);
        taskRepository.save(task);
    }

    private String verdictOf(TaskEntity task) {
        if (task.getPayload() == null) {
            return null;
        }
        String value = task.getPayload().path(VERDICT_KEY).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    // Carries the witness, not the fact alone: the criterion, the pull request and the judgment's own words,
    // so the worker who picks this up does not have to rediscover what the system already knows.
    private void fileRefutation(ProjectEntity project, TaskEntity task, String prUrl, String reason) {
        String marker = "task " + task.getId();
        boolean alreadyFiled = wishlistRepository
                .findByProjectIdAndStatus(project.getId(), WishlistStatus.pending).stream()
                .anyMatch(existing -> existing.getSource() == WishlistSource.delivery_refuted
                        && existing.getContent() != null && existing.getContent().contains(marker));
        if (alreadyFiled) {
            return;
        }

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(WishlistSource.delivery_refuted);
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setLeanValue(LeanValue.essential);
        wishlist.setCynefinDomain("clear");
        wishlist.setSourceRoleTag("BARCAN-TAG-00");
        wishlist.setContent("Work was accepted that does not satisfy what it promised.\n\n"
                + "The closed " + marker + " (\"" + task.getTitle() + "\") carried acceptance criteria, and "
                + "the diff merged for it does not meet them.\n\n"
                + "Acceptance criteria the task carried:\n" + task.getAcceptanceCriteria() + "\n\n"
                + "Merged pull request:\n" + prUrl + "\n\n"
                + "What the judgment found, in its own words - this is the evidence, not a summary of it:\n"
                + (reason == null || reason.isBlank() ? "(no reason recorded)" : reason) + "\n\n"
                + "Deliver the part of those criteria the merged change does not meet. Do not reopen the "
                + "closed task and do not restate the criteria as new work: what is missing is named above.");
        wishlist.setJtbd("When work has been accepted that does not do what it promised, I want the missing "
                + "part delivered against the criteria the task already carried, so that done means the same "
                + "thing on every task rather than meaning that a pull request merged.");
        wishlist.setAcceptanceCriteria("Given the acceptance criteria quoted above, When this finding is "
                + "delivered, Then each criterion the judgment named as unmet is met by code in this "
                + "project's repository, and the file or pull request that meets it is named.");
        wishlist.setDod("BARCAN-TAG-00: the delivered change satisfies the acceptance criteria quoted in "
                + "this finding, and the judgment's stated gap is closed rather than restated.");
        wishlistRepository.save(wishlist);

        log.warn("[DELIVERY-JUDGMENT] filed a refutation for task {} ({}): {}",
                task.getId(), task.getTitle(), reason);
    }

    private String firstWord(String answer) {
        String line = firstLine(answer);
        int space = line.indexOf(' ');
        return (space < 0 ? line : line.substring(0, space)).trim();
    }

    private String firstLine(String answer) {
        int newline = answer.indexOf('\n');
        return (newline < 0 ? answer : answer.substring(0, newline)).trim();
    }

    private String reasonOf(String answer) {
        int newline = answer.indexOf('\n');
        return newline < 0 ? "" : answer.substring(newline + 1).trim();
    }
}
