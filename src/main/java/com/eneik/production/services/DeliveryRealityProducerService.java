package com.eneik.production.services;

import com.eneik.production.models.persistence.EvidenceNodeEntity;
import com.eneik.production.models.persistence.OperationalRealityFindingEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.logging.LogScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Turns the readiness invariant's own detection into evidence.
 *
 * A task whose status says {@code done} while nothing ever reached main is the substitutivity error the
 * corpus forbids - {@code task done} standing in for {@code value delivered}. `ProjectFlowService.computeBlockedItems`
 * has detected exactly this since 2026-07-25 and labels it `done_not_reached_main`, and the detection reaches
 * `ProductReadinessDto` and stops: a dashboard field, consumed by no reasoner. That is defect D4 - a signal
 * with no reader is not monitoring - sitting on the most consequential signal in the system.
 *
 * Measured 2026-08-17 on the live project: `f163e834` "Runtime Contract 8becdc01", status `done`, no Jules
 * session, no dispatch status, no PR, no featureId. Zero evidence nodes named it. `OpsAuditorService`
 * gathers evidence only about `failed` tasks and orphaned wishlists; the operational-reality detector in
 * `AutoMergeService` compares a session's self-reported status against GitHub and so cannot speak about a
 * task that never had a session. The project stood at 5/6 features and 25/26 merged tasks with this as its
 * single blocked item - one item short of readiness 1.0, the design shop's threshold and above the
 * philosophical track's 0.9.
 *
 * Three deliberate constraints:
 *
 * 1. **It does not live on the read path.** `computeBlockedItems` runs on every dashboard request, and
 *    OPERATIONAL_MATH_ARCHITECTURE's boundary is that the read model does not write back. This is a
 *    separate scheduled producer.
 * 2. **It does not copy the predicate.** Charter invariant 10 requires one point of application for a
 *    shared invariant, so this consults the same {@link ClientDeliverableReadinessService#reachedMain} and
 *    {@link ClientDeliverableReadinessService#isAuxiliaryTask} the dashboard consults. If the invariant
 *    changes, both change together - evidence-gathering can never silently diverge from what is verified.
 * 3. **It is value-delivery scope, not factory scope.** The finding is about what this project has
 *    actually delivered, so the evidence node carries the project id. Factory problems, value-delivery
 *    problems and product problems are never mixed, and corroboration is computed within a type.
 *
 * Idempotent by Charter invariant 4: an existing finding for the task means the fact is already recorded,
 * which is "already done, nothing to do" rather than an error. A standing condition therefore yields one
 * piece of evidence, not one per sweep.
 */
@Service
public class DeliveryRealityProducerService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRealityProducerService.class);

    /** VARCHAR(64) in V82 - a longer descriptor silently dropped the row before, see AutoMergeService. */
    private static final String NO_MERGE_EVIDENCE = "no merge evidence: never reached main";

    /** VARCHAR(64) in V82 - kept short deliberately, see AutoMergeService. */
    private static final String LAUNCH_FAILED_STATE = "runtime observation unhealthy: did not launch";

    /**
     * A launch failure belongs to the PROJECT, not to any one task, but OperationalRealityFindingEntity
     * requires a task id. A fixed sentinel keeps the dedup/refresh logic identical to the per-task case
     * without inventing a task that does not exist - the project id on the evidence node carries the real
     * ownership.
     */
    private static final java.util.UUID RUNTIME_OBSERVATION_PSEUDO_TASK =
            java.util.UUID.fromString("00000000-0000-0000-0000-00000000fa11");

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final OperationalRealityFindingRepository operationalRealityFindingRepository;
    private final EvidenceNodeRepository evidenceNodeRepository;
    private final com.eneik.production.repositories.WishlistRepository wishlistRepository;
    /** Asked, never re-derived - see isWorkThatNeverLanded. */
    private final PlannedWorkRecoveryService plannedWorkRecoveryService;

    /**
     * Optional, field-injected for the same reason FactorySelfHealthService injects KaizenService that way:
     * this producer must keep working, and stay unit-testable, when the collaborator is absent. Its absence
     * degrades safely - the runtime-observation evidence is simply not produced.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.services.runtime.ClientRuntimeObservabilityService runtimeObservabilityService;

    public DeliveryRealityProducerService(ProjectRepository projectRepository,
                                          TaskRepository taskRepository,
                                          ClientDeliverableReadinessService readinessService,
                                          OperationalRealityFindingRepository operationalRealityFindingRepository,
                                          EvidenceNodeRepository evidenceNodeRepository,
                                          com.eneik.production.repositories.WishlistRepository wishlistRepository,
                                          PlannedWorkRecoveryService plannedWorkRecoveryService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.readinessService = readinessService;
        this.operationalRealityFindingRepository = operationalRealityFindingRepository;
        this.evidenceNodeRepository = evidenceNodeRepository;
        this.wishlistRepository = wishlistRepository;
        this.plannedWorkRecoveryService = plannedWorkRecoveryService;
    }

    /**
     * The work that never landed, ordered again.
     *
     * 2026-08-23. Detection has existed since 2026-07-25 and evidence since 2026-08-17, and neither ever
     * became work: the finding was written, the evidence node was written, and every hour after that this
     * producer reported "0 new findings, 1 already recorded" - correctly, and to no effect. Measured on
     * test-fiftieth: `Runtime Contract 9b58412d` stood done-without-merge for ten and a half hours and held
     * assembly at 17 of 18 merged tasks while all of that machinery worked exactly as designed.
     *
     * Evidence from which no action follows is not evidence in this system. A signal with a reader and no
     * actor is the same defect as a signal with no reader, one step further along, and it is the third of
     * that shape found in a single day - after the auditor's flagForHumanReview and the gates that recorded
     * a pass where no check had applied.
     */
    // Package-private for P5 (§9): the split of marker's two jobs has to be asserted on BOTH halves,
    // and breaking one side of a boundary without noticing the other is this session's recurring defect.
    void fileTheMissingWorkAsScope(ProjectEntity project, TaskEntity task) {
        // Deduplicated on the identifier, not on a substring of the brief's own prose (V116, §9). The
        // old form did both jobs with one string: it matched `"task " + id` inside the text, which meant
        // the id HAD to be in text an agent working in the client's codebase reads. That is the zone
        // boundary of §2's first invariant, and crossing it is what made a nonsense pull request the only
        // action available to that agent. The link now lives in a column; the text no longer carries it.
        if (wishlistRepository.existsByProjectIdAndSourceAndSourceTaskId(
                project.getId(),
                com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main,
                task.getId())) {
            return;
        }

        String title = com.eneik.production.services.task.TaskTitleBuilder.displayTitle(task);
        com.eneik.production.models.persistence.WishlistEntity wishlist =
                new com.eneik.production.models.persistence.WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main);
        wishlist.setSourceTaskId(task.getId());
        // 2026-08-23. A finding inherits the epic of the task it is about. Without this the wishlist it
        // produces compiles into a task with no feature, and a feature closes when ITS tasks close - so the
        // work exists, runs, merges, and moves nothing. Measured on test-fiftieth: eighty-two tasks, four
        // epics closed, and the one epic that carries what the client actually asked for - upload, search,
        // download - stuck at 6 of 7 while task after task completed beside it. Repairing delivery of a
        // task and detaching that repair from the task's own feature is delivering into a void.
        // Model rule 8.18.1: a repair belongs to the epic of the requirement it repairs and never founds
        // one of its own. This used to copy the task's featureId even when it was null - the brief then
        // reached the compiler with no epic, and resolveOrCreateFeatureId minted a fresh one rooted in the
        // REPAIR BRIEF itself. That brief's source is delivery_never_reached_main, outside
        // PRODUCT_ITERATION_SOURCES, so the epic never enters the product set, and every later repair
        // inherits it. Measured 2026-09-02: of 444 repairs, 434 carried an epic outside the product set,
        // spread over 30 such epics against nine real ones - work that runs, merges and moves nothing.
        UUID epic = epicOfRequirement(task);
        if (epic == null) {
            log.warn("DeliveryRealityProducerService: not filing scope for task {} - no epic is reachable "
                            + "from it, and a repair may not found one (model rule 8.18.1). The finding "
                            + "itself stays on record.", task.getId());
            return;
        }
        wishlist.setFeatureId(epic);
        wishlist.setOriginFeatureId(task.getOriginFeatureId() != null
                ? task.getOriginFeatureId() : task.getFeatureId());
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        wishlist.setLeanValue(com.eneik.production.models.persistence.LeanValue.essential);
        wishlist.setCynefinDomain("clear");
        wishlist.setSourceRoleTag("BARCAN-TAG-00");
        wishlist.setContent("Planned work never reached the main branch.\n\n"
                + "The closed task \"" + title + "\" " + whatItsRecordSays(task) + "\n\n"
                + whatWasAlreadyJudged(task)
                + "Deliver what that task was for. Do not reopen it and do not restate its goal as new "
                + "scope: what is missing is the change itself, on main.");
        wishlist.setJtbd("When planned work ends with nothing of it on main, I want the work itself "
                + "delivered, so that the record of a task and the state of the branch say the same thing.");
        wishlist.setAcceptanceCriteria("Given the task named above, When this finding is delivered, Then a "
                + "merged pull request exists on main carrying the change that task was for, and it is "
                + "named here.");
        wishlist.setDod("BARCAN-TAG-00: the change the named task was for is present on main in a merged "
                + "pull request, and the task's status is no longer the only evidence of delivery.");
        wishlistRepository.save(wishlist);

        log.warn("DeliveryRealityProducerService: filed missing delivery of task {} ({}) as scope - "
                + "status {}, nothing reached main", task.getId(), title, task.getStatus());
    }

    /**
     * The ground of the denial this repair answers, in the words it was recorded in (model rule 8.22).
     *
     * <p>A repair is a turn of a cycle, and rule 8.4 requires every cycle to carry something that strictly
     * decreases. Reissuing the failed order unchanged decreases nothing: the next agent gets the same task,
     * in the same words, knowing exactly what the last one knew, and produces the same result. The factory
     * already records why the previous attempt was judged as it was - passing that on is what makes the
     * next turn a different turn.
     *
     * <p>Returns an empty string when no ground was recorded. A denial with no recorded ground must not be
     * given an invented one: that would be the defect rule 8.3.1 exists to prevent, committed by the
     * repairer instead of the denier.
     */
    private String whatWasAlreadyJudged(TaskEntity task) {
        String verdict = task.acceptanceVerdict();
        if (verdict == null) {
            return "";
        }
        String reason = task.acceptanceVerdictReason();
        if (reason == null) {
            return "Its delivery was already judged " + verdict + ", with no ground recorded.\n\n";
        }
        return "Its delivery was already judged " + verdict + ", on this ground: " + reason
                + "\n\nWhatever you do must answer that ground. Repeating what the previous attempt did "
                + "will produce the same judgement again.\n\n";
    }

    /**
     * What this task's own record actually says, in one sentence, so every place that states the finding
     * states the truth about it.
     *
     * <p>This sweep speaks about two different situations and used to describe both as the first one. A
     * brief that misstates the fact it is about is the same boundary defect this file already paid for
     * once: the agent reads the brief, not the code, and acts on whatever it is told.
     */
    private String whatItsRecordSays(TaskEntity task) {
        if (task.getStatus() == TaskStatus.done) {
            return "has status done, and no merge evidence exists for it at all - no merged pull request, "
                    + "nothing on main. Its own status is the only thing asserting that the work was delivered.";
        }
        return "has status " + task.getStatus() + ", no merge evidence exists for it at all - no merged pull "
                + "request, nothing on main - and nothing is going to retry it: its automatic recovery no "
                + "longer applies. The work it was planned for is simply absent.";
    }

    /**
     * Also at startup, not only on the hour.
     *
     * 2026-08-23. An hourly cron was this detector's only trigger, so after any deploy the system stayed
     * wrong for up to an hour before anything looked - and a repair could not be shown to work without
     * waiting out a dead interval. Measured twice today on the same task: `Runtime Contract 9b58412d` sat
     * blocked while two successive versions of its fix were deployed and neither had yet been given a tick.
     * A producer that reconciles reality with the record has no reason to wait for a clock when the record
     * has just changed underneath it.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void produceOnStartup() {
        produce();
    }

    /** How many repairs sit outside the product epic set, per project, as last reported. */
    private final java.util.Map<UUID, String> lastStrayRepairs = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * How much repair work is attached to an epic the value model does not count (model rule 8.18.1).
     *
     * <p>Value is measured by the completeness of epics in the product set. A repair whose epic is outside
     * that set runs, merges and moves nothing. Measured 2026-09-02: of 444 repairs, 434 carried an epic
     * outside the set, spread over 30 such epics against nine real ones. Filing was fixed the same evening
     * so a repair can no longer found an epic of its own - but the rows already written stay where they
     * are, and whether they can be re-homed depends on whether a product epic is REACHABLE from each of
     * them through the repair chain. That is what this counts, and it is the number a migration would have
     * to stand on.
     */
    private void reportStrayRepairsIfChanged(ProjectEntity project, List<TaskEntity> projectTasks,
            List<com.eneik.production.models.persistence.WishlistEntity> allWishlist) {
        java.util.Set<UUID> productEpics = readinessService.listEpicDiagnostics(project.getId()).stream()
                .map(ClientDeliverableReadinessService.EpicDiagnostic::id)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<UUID, com.eneik.production.models.persistence.WishlistEntity> wishlistById =
                new java.util.HashMap<>();
        for (com.eneik.production.models.persistence.WishlistEntity item : allWishlist) {
            wishlistById.put(item.getId(), item);
        }
        java.util.Map<UUID, TaskEntity> taskById = new java.util.HashMap<>();
        for (TaskEntity task : projectTasks) {
            taskById.put(task.getId(), task);
        }

        int inSet = 0;
        int outside = 0;
        int reHomeable = 0;
        // How each stray epic came to be there. Rule 8.18.1 asks this before anything is changed, because
        // the two answers need different repairs and the counts say which one exists: a repair that carries
        // the epic of the task it repairs inherited a stray that was already there, and the chain is what
        // has to be cut; a repair carrying a different epic founded one on itself, which is the filing site.
        java.util.Map<String, Long> strayOrigin = new java.util.TreeMap<>();
        // And WHAT each stray repair is repairing. Every stray inherited its epic from the task it repairs
        // (measured 2026-09-03: origin {inherited=125}), so the epic is not the question any more - the
        // question is whether those tasks are product work that lost its requirement or carriers, because
        // a carrier that did not deliver must not order product scope at all (model rule 8.2), while
        // product work that lost its epic has to be returned to the requirement it was an attempt at.
        java.util.Map<String, Long> strayRepaired = new java.util.TreeMap<>();
        for (com.eneik.production.models.persistence.WishlistEntity item : allWishlist) {
            if (item.getSourceTaskId() == null) {
                continue;
            }
            UUID epic = item.getFeatureId();
            if (epic != null && productEpics.contains(epic)) {
                inSet++;
                continue;
            }
            outside++;
            if (reachableProductEpic(item, wishlistById, taskById, productEpics) != null) {
                reHomeable++;
            }
            strayOrigin.merge(strayEpicOrigin(item, taskById), 1L, Long::sum);
            strayRepaired.merge(whatIsBeingRepaired(item, wishlistById, taskById), 1L, Long::sum);
        }
        String digest = inSet + "/" + (inSet + outside) + " in set, " + reHomeable + " of " + outside
                + " re-homeable" + (strayOrigin.isEmpty() ? "" : ", origin " + strayOrigin)
                + (strayRepaired.isEmpty() ? "" : ", repairing " + strayRepaired)
                + ", " + repairBriefsNamingNothing(allWishlist);
        if (digest.equals(lastStrayRepairs.get(project.getId()))) {
            return;
        }
        lastStrayRepairs.put(project.getId(), digest);
        log.info("DeliveryRealityProducerService: project {} - repairs by epic: {} (model rule 8.18.1)",
                project.getName(), digest);
    }

    /**
     * How many repair briefs do not name the task they repair.
     *
     * <p>A repair is keyed on the task it repairs: that link is what puts it inside the requirement's
     * closure (rule 8.18), what makes the repair chain's depth observable (rule 8.21), and what the
     * requirement report counts as {@code repairs=N/M}. A brief without it is work ordered for a
     * requirement that can never count it - present in the project, absent from every closure.
     *
     * <p>Measured indirectly first, and the two readings disagreed: 89 stray repairs name a task that came
     * from a repair brief, so their chains are second-order, while the depth histogram reported every chain
     * as first-order. The walk stops at exactly one place - a repair brief with no task named - so this
     * counts that place directly instead of inferring it.
     */
    String repairBriefsNamingNothing(List<com.eneik.production.models.persistence.WishlistEntity> allWishlist) {
        long briefs = 0;
        long slices = 0;
        long namingNothing = 0;
        for (com.eneik.production.models.persistence.WishlistEntity item : allWishlist) {
            if (item.getSource() != com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main) {
                continue;
            }
            briefs++;
            if (item.getSourceTaskId() != null) {
                continue;
            }
            // A compiled slice inherits its parent's source and is linked by the wishlist it came from,
            // not by a repaired task - rule 8.18 already walks that link. Counting it as a brief that
            // names nothing would report a defect where the model has a link.
            if (item.getOriginWishlistId() != null) {
                slices++;
            } else {
                namingNothing++;
            }
        }
        return "repair briefs " + briefs + ", of them slices " + slices + ", naming nothing " + namingNothing;
    }

    /**
     * The role and origin of the task a stray repair is repairing, named as "roleTag/wishlistSource" so
     * carriers and product work are told apart by what they are rather than by what they are called.
     */
    String whatIsBeingRepaired(com.eneik.production.models.persistence.WishlistEntity repair,
                               java.util.Map<UUID, com.eneik.production.models.persistence.WishlistEntity> wishlistById,
                               java.util.Map<UUID, TaskEntity> taskById) {
        TaskEntity repaired = taskById.get(repair.getSourceTaskId());
        if (repaired == null) {
            return "repaired-task-gone";
        }
        String role = repaired.getRole() == null || repaired.getRole().getTag() == null
                ? "no-role" : repaired.getRole().getTag();
        com.eneik.production.models.persistence.WishlistEntity origin =
                repaired.getSourceWishlistId() == null ? null : wishlistById.get(repaired.getSourceWishlistId());
        String source = origin == null || origin.getSource() == null
                ? "no-source" : origin.getSource().name();
        return role + "/" + source;
    }

    /**
     * Where a repair's out-of-set epic came from, which is the distinction rule 8.18.1 requires be
     * established by measurement before the code is touched.
     *
     * <ul>
     *   <li>{@code inherited} - the repair carries exactly the epic of the task it repairs, so the stray
     *       was already there and travelled down the chain.
     *   <li>{@code founded} - the repair carries an epic that is not the repaired task's, so it was made
     *       here rather than inherited. A repair may not found an epic of its own.
     *   <li>{@code none} - the repair carries no epic at all, so it belongs to nothing.
     *   <li>{@code repaired-task-gone} - the task it names no longer exists, so origin is unanswerable
     *       from this row and must not be counted as either.
     * </ul>
     */
    String strayEpicOrigin(com.eneik.production.models.persistence.WishlistEntity repair,
                                   java.util.Map<UUID, TaskEntity> taskById) {
        TaskEntity repaired = taskById.get(repair.getSourceTaskId());
        if (repaired == null) {
            return "repaired-task-gone";
        }
        if (repair.getFeatureId() == null) {
            return "none";
        }
        return repair.getFeatureId().equals(repaired.getFeatureId()) ? "inherited" : "founded";
    }

    /** The nearest product epic reachable from a repair through its chain, or null when none is. */
    private UUID reachableProductEpic(com.eneik.production.models.persistence.WishlistEntity repair,
                                      java.util.Map<UUID, com.eneik.production.models.persistence.WishlistEntity> wishlistById,
                                      java.util.Map<UUID, TaskEntity> taskById,
                                      java.util.Set<UUID> productEpics) {
        java.util.Set<UUID> visited = new java.util.HashSet<>();
        com.eneik.production.models.persistence.WishlistEntity current = repair;
        while (current != null && visited.add(current.getId())) {
            if (current.getFeatureId() != null && productEpics.contains(current.getFeatureId())) {
                return current.getFeatureId();
            }
            if (current.getSourceTaskId() == null) {
                return null;
            }
            TaskEntity repaired = taskById.get(current.getSourceTaskId());
            if (repaired == null) {
                return null;
            }
            if (repaired.getFeatureId() != null && productEpics.contains(repaired.getFeatureId())) {
                return repaired.getFeatureId();
            }
            if (repaired.getSourceWishlistId() == null) {
                return null;
            }
            current = wishlistById.get(repaired.getSourceWishlistId());
        }
        return null;
    }

    /** The repair-chain depth histogram, per project, as last reported. */
    private final java.util.Map<java.util.UUID, String> lastRepairDepths = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * How deep the repair chains of this project have grown (model rule 8.21).
     *
     * <p>Filing a repair for work that did not deliver is a TURN OF A CYCLE: the repair's own task can fail
     * to deliver too, and then a repair is filed for it. Rule 8.4 requires every cycle to carry a quantity
     * that strictly decreases per turn, and this one carries none - each turn mints a fresh task identity,
     * so resumeCount, retryCount and compileAttempts all stay where they were.
     *
     * <p>Measured 2026-09-02 over three days without intervention: 408 done tasks became 488, 410 merged
     * reviews became 496, delivered requirements stayed at 13 of 19, and the parked set - work that reached
     * main without the artifact its role owes - went from 16 to 50, of which 39 carry BARCAN-TAG-00, the
     * role this department stamps on its own repair briefs. Work producing work, delivering nothing.
     *
     * <p>This only MEASURES. The bound on chain depth has to be derived from the distribution, not assumed,
     * exactly as 4.36 requires of the decomposition budget. Depth needs no new column: a repair names the
     * task it repairs, and that task names the wishlist it came from.
     */
    private void reportRepairDepthsIfChanged(ProjectEntity project, List<TaskEntity> projectTasks,
            List<com.eneik.production.models.persistence.WishlistEntity> allWishlist) {
        java.util.Map<java.util.UUID, com.eneik.production.models.persistence.WishlistEntity> wishlistById =
                new java.util.HashMap<>();
        for (com.eneik.production.models.persistence.WishlistEntity item : allWishlist) {
            wishlistById.put(item.getId(), item);
        }
        java.util.Map<java.util.UUID, TaskEntity> taskById = new java.util.HashMap<>();
        for (TaskEntity task : projectTasks) {
            taskById.put(task.getId(), task);
        }

        java.util.TreeMap<Integer, Long> histogram = new java.util.TreeMap<>();
        for (com.eneik.production.models.persistence.WishlistEntity item : allWishlist) {
            if (item.getSourceTaskId() == null) {
                continue;
            }
            histogram.merge(repairDepth(item, wishlistById, taskById), 1L, Long::sum);
        }
        String digest = histogram.toString();
        if (digest.equals(lastRepairDepths.get(project.getId()))) {
            return;
        }
        lastRepairDepths.put(project.getId(), digest);
        log.info("DeliveryRealityProducerService: project {} - repair chain depths {} (model rule 8.21: this "
                        + "cycle has no variant function yet)", project.getName(), digest);
    }

    /**
     * The epic of the requirement this task is an attempt at, walked back through the repair chain
     * (model rules 8.18 and 8.18.1).
     *
     * <p>A task without an epic of its own is an attempt at a requirement that has one - the chain reaches
     * it through the wishlist the task came from and, when that wishlist is itself a repair, through the
     * task it repairs. Returns null only when nothing in the chain carries an epic; the caller then files
     * nothing rather than founding one, because an epic founded on a repair brief is outside the product
     * set by construction and can never come back into it.
     */
    private UUID epicOfRequirement(TaskEntity task) {
        if (task.getFeatureId() != null) {
            return task.getFeatureId();
        }
        java.util.Set<UUID> visited = new java.util.HashSet<>();
        TaskEntity current = task;
        while (current != null && current.getId() != null && visited.add(current.getId())) {
            UUID sourceWishlistId = current.getSourceWishlistId();
            if (sourceWishlistId == null) {
                return null;
            }
            com.eneik.production.models.persistence.WishlistEntity origin =
                    wishlistRepository.findById(sourceWishlistId).orElse(null);
            if (origin == null) {
                return null;
            }
            if (origin.getFeatureId() != null) {
                return origin.getFeatureId();
            }
            if (origin.getSourceTaskId() == null) {
                return null;
            }
            current = taskRepository.findById(origin.getSourceTaskId()).orElse(null);
        }
        return null;
    }

    /** Depth of one repair chain, walked back through the task each link repairs. Visited-guarded. */
    private int repairDepth(com.eneik.production.models.persistence.WishlistEntity repair,
                            java.util.Map<java.util.UUID, com.eneik.production.models.persistence.WishlistEntity> wishlistById,
                            java.util.Map<java.util.UUID, TaskEntity> taskById) {
        java.util.Set<java.util.UUID> visited = new java.util.HashSet<>();
        int depth = 1;
        com.eneik.production.models.persistence.WishlistEntity current = repair;
        while (current != null && current.getSourceTaskId() != null && visited.add(current.getId())) {
            TaskEntity repaired = taskById.get(current.getSourceTaskId());
            if (repaired == null || repaired.getSourceWishlistId() == null) {
                return depth;
            }
            com.eneik.production.models.persistence.WishlistEntity previous =
                    wishlistById.get(repaired.getSourceWishlistId());
            if (previous == null || previous.getSourceTaskId() == null) {
                return depth;
            }
            depth++;
            current = previous;
        }
        return depth;
    }

    /** How large the predicate disagreement is, per project, as last reported. */
    private final java.util.Map<java.util.UUID, String> lastDisagreement = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * How many requirements sit in the gap between "reached main" and "delivered" (model rule 8.19).
     *
     * <p>This department selects on {@code reachedMain}, which is true for any merged pull request; value is
     * counted with {@code hasRequiredMergeEvidence}, which for a code-owing role also requires the diff to
     * carry code. A task whose merged pull request records a blocker rather than the work satisfies the
     * first and fails the second: this department does not see it, the value count does not credit it, and
     * the requirement is parked with nothing anywhere saying so. Measured 2026-08-30: of 279 wishlists in
     * the project not one mentions a blocker, while the live circuit was merging pull requests titled
     * "Record concrete blocker ..." and "Blocker: Unresolvable specification misalignment ...".
     *
     * <p>This only MEASURES. Switching the selection would file one repair per task in this set, and the
     * set's size is exactly what is not yet known - a number that must be measured before it is acted on,
     * never assumed (plan 4.47).
     */
    private void reportPredicateDisagreementIfChanged(ProjectEntity project, List<TaskEntity> projectTasks) {
        java.util.Map<String, Long> byRole = projectTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.done)
                .filter(task -> !readinessService.isAuxiliaryTask(task))
                .filter(readinessService::reachedMain)
                .filter(task -> !readinessService.hasRequiredMergeEvidence(task))
                .collect(java.util.stream.Collectors.groupingBy(
                        task -> {
                            String role = task.getRole() == null ? "(none)" : task.getRole().getTag();
                            return role + "/" + com.eneik.production.services.EmsFlowStage.deliveryArtifact(role);
                        },
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        long parked = byRole.values().stream().mapToLong(Long::longValue).sum();
        String digest = parked + " " + byRole;
        if (digest.equals(lastDisagreement.get(project.getId()))) {
            return;
        }
        lastDisagreement.put(project.getId(), digest);
        log.info("DeliveryRealityProducerService: project {} - {} done task(s) reached main without the "
                        + "artifact their role owes, so their requirement is neither delivered nor spoken "
                        + "about (plan 4.47); by role/artifact: {}",
                project.getName(), parked, byRole);
    }

    /**
     * Planned work that did not land and that nothing is going to land.
     *
     * <p>Model rule 8.12: every department's finding maps to a wishlist and re-enters the chain. This
     * department owns "the work never reached main". Its predicate used to be {@code status == done} alone,
     * which reads the status word rather than the deliverable, and so left a whole class outside every
     * department: a task that FAILED and that PlannedWorkRecoveryService durably refuses. Nobody speaks
     * about those, so nothing orders them again.
     *
     * <p>Measured on the live circuit 2026-08-30: 382 done, 38 failed and nothing else - no queued, no
     * claimed, no review - with six planned client deliverables unmerged, seven accounts free, and the
     * project standing in VERIFYING_DELIVERY for 3632 minutes against a 30-minute SLA. That is model rule
     * 8.11 L5 failing outright: admissible work and free capacity, and nothing advances.
     *
     * <p>A failed task the recovery service can still resume is deliberately NOT filed here. That service
     * owns it, reuses its task identity, and ordering the same work twice in parallel is what the
     * non-parallelism rule forbids. The question is asked through
     * {@link PlannedWorkRecoveryService#mayStillBeResumed} rather than re-derived, so the two departments
     * cannot drift into either double-ordering or a gap between them (Charter invariant 10).
     */
    boolean isWorkThatNeverLanded(TaskEntity task) {
        if (task.getStatus() == TaskStatus.done) {
            return true;
        }
        return task.getStatus() == TaskStatus.failed && !plannedWorkRecoveryService.mayStillBeResumed(task);
    }

    @Scheduled(cron = "${delivery-reality-producer.cron:0 20 * * * ?}")
    public void produce() {
        List<ProjectEntity> active = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.active)
                .toList();
        for (ProjectEntity project : active) {
            LogScope.project(project.getId());
            try {
                produceForProject(project);
            } catch (Exception e) {
                log.error("DeliveryRealityProducerService: failed for project {}: {}",
                        project.getId(), e.getMessage(), e);
            } finally {
                LogScope.clear();
            }
        }
    }

    private void produceForProject(ProjectEntity project) {
        produceRuntimeObservationEvidence(project);
        int recorded = 0;
        int alreadyKnown = 0;
        // Model rule 8.11 O8: a record that cannot be read correctly is not a record. `alreadyKnown` sums
        // refreshStandingEvidence's return value, which counts EVIDENCE NODES refreshed, not tasks - and it
        // was reported next to a task count, so the two invited exactly the subtraction that produced a
        // false finding on 2026-09-02 ("50 parked, 16 recorded, therefore 34 unspoken for"). The sweep now
        // says how many TASKS it saw, separately from how many nodes it refreshed.
        int tasksSeen = 0;
        // One point of application (Charter, inv. 10). This sweep asked the database for the project's
        // whole task table four times and its whole wishlist table twice, each caller building its own maps
        // over the same rows. Measured 2026-09-03 while the flow stood still: the factory backend sat at
        // 863 MiB of its 1 GiB limit burning two cores, Hikari reported thread starvation with an 84-second
        // housekeeper delta, and the read path that had answered in 4.6 seconds took over 200. The rows are
        // the same rows and the answers are the same answers - reading them once per sweep is not a cache,
        // it is the same query asked once instead of six times.
        List<TaskEntity> projectTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        List<com.eneik.production.models.persistence.WishlistEntity> projectWishlist =
                wishlistRepository.findByProjectId(project.getId());
        reportPredicateDisagreementIfChanged(project, projectTasks);
        reportRepairDepthsIfChanged(project, projectTasks, projectWishlist);
        reportStrayRepairsIfChanged(project, projectTasks, projectWishlist);
        for (TaskEntity task : projectTasks) {
            if (!isWorkThatNeverLanded(task)) {
                continue;
            }
            // Same two checks the dashboard applies - a DECISION-stage or `complex`-Cynefin task is never
            // expected to reach main on its own, and flagging it would be the 2026-07-25 false positive.
            // Model rule 8.19: a merged diff delivers the requirement only if it carries code, for a role
            // that owes code. This asked reachedMain, which is true for ANY merged pull request, while value
            // is counted with hasRequiredMergeEvidence, which also requires the diff to carry code. A task
            // whose merged pull request records a blocker rather than the work satisfied the first and
            // failed the second: this department did not see it, the value count did not credit it, and the
            // requirement was parked with nothing anywhere saying so. Measured 2026-08-30: 16 such tasks in
            // one project, against 400 done and 13 of 19 requirements delivered. Both questions are now
            // answered by one predicate (Charter invariant 10).
            if (readinessService.hasRequiredMergeEvidence(task) || readinessService.isAuxiliaryTask(task)) {
                continue;
            }
            tasksSeen++;
            if (!operationalRealityFindingRepository.findByTaskId(task.getId()).isEmpty()) {
                alreadyKnown += refreshStandingEvidence(task);
                // 2026-08-23, second pass. The scope filing below sat AFTER this continue, so it could only
                // ever run for a finding recorded for the first time - and the task it was written for had
                // been recorded the day before. Measured: `Runtime Contract 9b58412d` stayed blocked for
                // 11.9 hours with the fix deployed and unreachable. A repair placed on the path the defect
                // does not take is not a repair. It belongs on both paths, because the question "has this
                // work been ordered again" is independent of whether the finding is new.
                fileTheMissingWorkAsScope(project, task);
                continue;
            }
            OperationalRealityFindingEntity finding = new OperationalRealityFindingEntity();
            finding.setTaskId(task.getId());
            // Null since V103: a session is one KIND of record, not the essence of the claim, and this task
            // asserts completion having never had one.
            finding.setJulesSessionId(null);
            // The task's own status, not the word `done`. This sweep now also speaks about abandoned
            // `failed` work, and recording it as having claimed completion would be a false statement in
            // the evidence record - the one place that must never carry one.
            finding.setExpectedStatus(task.getStatus().name());
            finding.setActualGithubState(NO_MERGE_EVIDENCE);
            finding = operationalRealityFindingRepository.save(finding);

            EvidenceNodeEntity node = new EvidenceNodeEntity();
            node.setProjectId(project.getId());
            node.setFeatureId(task.getFeatureId());
            node.setPolarity(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
            node.setSummaryText("Task " + task.getId() + " ("
                    + com.eneik.production.services.task.TaskTitleBuilder.displayTitle(task)
                    + ") " + whatItsRecordSays(task));
            node.setOperationalRealityFindingId(finding.getId());
            evidenceNodeRepository.save(node);
            fileTheMissingWorkAsScope(project, task);
            recorded++;

            log.warn("DeliveryRealityProducerService: task {} in project {} reports done with no merge "
                            + "evidence - recorded as operational reality finding {}",
                    task.getId(), project.getName(), finding.getId());
        }
        if (tasksSeen > 0) {
            log.info("DeliveryRealityProducerService: project {} - {} task(s) with nothing delivered: {} "
                            + "newly recorded, {} already on record ({} evidence node(s) refreshed)",
                    project.getName(), tasksSeen, recorded, tasksSeen - recorded, alreadyKnown);
        }
    }

    /**
     * Keeps a standing condition's evidence current without growing the number of rows.
     *
     * Measured 2026-08-18: this producer is idempotent, so it recorded the finding for f163e834 once, at
     * 22:20:00Z, and skipped every sweep after. Both readers of the graph select nodes by createdAt after
     * a window start - EvidenceCoherenceService.graphSnapshot and GeminiProjectObserverService:687, the
     * latter at 24 hours - so the node silently leaves both windows while the condition it reports is
     * still true. Idempotency and a bounded read window are each correct; together they turn a standing
     * fact into a historical note that stops being read.
     *
     * Refresh rather than re-emit, and the reason is not economy. The observer's tool loop terminates on
     * "a round that returns no evidence-node id she has not already seen this cycle" - an id-based signal,
     * chosen deliberately against the isolation problem of pure coherentism. Emitting a fresh node every sweep
     * would hand her an unseen id every time and defeat that termination; refreshing the same row cannot.
     *
     * What each timestamp means afterwards, stated rather than left to be inferred (ACP-101): the evidence
     * node's createdAt becomes **last confirmed still true**, and the OperationalRealityFindingEntity keeps
     * **first detected** in its own createdAt. Both facts survive, each where it is authoritative.
     *
     * Cardinality stays bounded at one node per task, forever, for as long as the condition holds.
     */
    private int refreshStandingEvidence(TaskEntity task) {
        List<OperationalRealityFindingEntity> findings = operationalRealityFindingRepository.findByTaskId(task.getId());
        int refreshed = 0;
        for (OperationalRealityFindingEntity finding : findings) {
            for (EvidenceNodeEntity node : evidenceNodeRepository.findByOperationalRealityFindingId(finding.getId())) {
                node.setCreatedAt(Instant.now());
                evidenceNodeRepository.save(node);
                refreshed++;
            }
        }
        return refreshed == 0 ? 1 : refreshed;
    }

    /**
     * Turns an unhealthy runtime observation into evidence the reasoner can actually read.
     *
     * Measured 2026-08-19 on the only ACTIVE project: the launcher observed launchSuccess=false at
     * 09:12:32Z and recorded it in ClientRuntimeObservationEntity - its own table, correctly, since that is
     * the append-only history of what the product really did. But the coherence graph held 52 nodes and
     * NOT ONE about launch, runtime, compose or the image: the observation is invisible to everything that
     * reasons over evidence nodes, including the project observer, whose 24-hour window is exactly that
     * graph.
     *
     * The consequence is not academic. The observer holds an action - triggerFalsificationRun - that pulls
     * the philosophical cycle forward instead of waiting for its 2-daily cron, and that cycle is what files
     * product_not_launchable. She has the instrument and no grounds to use it, so the constraint the whole
     * architecture subordinates to waits on a schedule while the evidence for it already exists. That is
     * D1: a fact the system holds but cannot represent in the medium its reasoner reads.
     *
     * Reuses the existing shape rather than adding one. An OperationalRealityFindingEntity is precisely
     * "the record disagrees with reality" - expected launchable, actually failed - and since V103 it no
     * longer presupposes a Jules session, which is what makes a launch failure expressible at all. One
     * point of application: the same finding type, the same node type, the same producer.
     *
     * Idempotent per Charter invariant 4 and refreshed while the condition holds, exactly like the
     * done-without-merge evidence above: one node per project for as long as the product is down, its
     * timestamp tracking "last confirmed still true" rather than "first noticed".
     */
    private void produceRuntimeObservationEvidence(ProjectEntity project) {
        if (runtimeObservabilityService == null) {
            return;
        }
        try {
            var health = runtimeObservabilityService.summarize(project.getId());
            if (health == null) {
                return;
            }
            if (!Boolean.FALSE.equals(health.lastObservationHealthy())) {
                return; // healthy, or no verdict yet - nothing to report
            }
            // 2026-08-21 (ACP-103): this used to read the newest row of ANY kind. The node it writes
            // asserts "expected launchable, actually failed" about the PRODUCT, so an unanswered launcher
            // call entering here put a fact about this factory's own sidecar into the coherence graph as
            // a product failure. lastObservationHealthy was already computed from the last real
            // observation; the cause must come from the same row, or the claim and its witness describe
            // different things.
            var latestOpt = health.lastProductObservation();
            if (latestOpt.isEmpty()) {
                return; // nothing has observed the product yet - there is no cause to cite
            }
            var latest = latestOpt.get();
            String cause = latest.getErrorText() == null ? "" : latest.getErrorText().trim();

            List<OperationalRealityFindingEntity> existing =
                    operationalRealityFindingRepository.findByTaskId(RUNTIME_OBSERVATION_PSEUDO_TASK);
            if (!existing.isEmpty()) {
                for (OperationalRealityFindingEntity f : existing) {
                    for (EvidenceNodeEntity node : evidenceNodeRepository.findByOperationalRealityFindingId(f.getId())) {
                        if (project.getId().equals(node.getProjectId())) {
                            node.setCreatedAt(Instant.now());
                            evidenceNodeRepository.save(node);
                            return;
                        }
                    }
                }
            }

            OperationalRealityFindingEntity finding = new OperationalRealityFindingEntity();
            finding.setTaskId(RUNTIME_OBSERVATION_PSEUDO_TASK);
            finding.setJulesSessionId(null);
            finding.setExpectedStatus("launchable");
            finding.setActualGithubState(LAUNCH_FAILED_STATE);
            finding = operationalRealityFindingRepository.save(finding);

            EvidenceNodeEntity node = new EvidenceNodeEntity();
            node.setProjectId(project.getId());
            node.setPolarity(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
            node.setSummaryText("The delivered product's most recent runtime observation was not healthy: it "
                    + "did not launch, or launched and failed its health check. Everything else - feature work, "
                    + "design, philosophical review - is reasoning about a product that does not run."
                    + (cause.isBlank() ? "" : " Observed cause, exactly as the launcher recorded it: " + cause));
            node.setOperationalRealityFindingId(finding.getId());
            evidenceNodeRepository.save(node);

            log.warn("DeliveryRealityProducerService: project {} is not launchable - recorded runtime "
                    + "observation as evidence so it is visible to reasoning, not only to the observation table",
                    project.getName());
        } catch (Exception e) {
            log.debug("DeliveryRealityProducerService: could not record runtime-observation evidence for {}: {}",
                    project.getId(), e.getMessage());
        }
    }
}
