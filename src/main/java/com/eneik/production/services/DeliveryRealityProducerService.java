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
                                          com.eneik.production.repositories.WishlistRepository wishlistRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.readinessService = readinessService;
        this.operationalRealityFindingRepository = operationalRealityFindingRepository;
        this.evidenceNodeRepository = evidenceNodeRepository;
        this.wishlistRepository = wishlistRepository;
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
    private void fileTheMissingWorkAsScope(ProjectEntity project, TaskEntity task) {
        String marker = "task " + task.getId();
        boolean alreadyFiled = wishlistRepository
                .findByProjectIdAndStatus(project.getId(), com.eneik.production.models.persistence.WishlistStatus.pending)
                .stream()
                .anyMatch(existing -> existing.getSource() == com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main
                        && existing.getContent() != null && existing.getContent().contains(marker));
        if (alreadyFiled) {
            return;
        }

        String title = com.eneik.production.services.task.TaskTitleBuilder.displayTitle(task);
        com.eneik.production.models.persistence.WishlistEntity wishlist =
                new com.eneik.production.models.persistence.WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main);
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        wishlist.setLeanValue(com.eneik.production.models.persistence.LeanValue.essential);
        wishlist.setCynefinDomain("clear");
        wishlist.setSourceRoleTag("BARCAN-TAG-00");
        wishlist.setContent("Work that was reported as delivered never reached the main branch.\n\n"
                + "The closed " + marker + " (\"" + title + "\") has status done, and no merge evidence "
                + "exists for it at all - no merged pull request, nothing on main. Its own status is the "
                + "only thing asserting that the work was delivered.\n\n"
                + "Deliver what that task was for. Do not reopen it and do not restate its goal as new "
                + "scope: what is missing is the change itself, on main.");
        wishlist.setJtbd("When a task says done while nothing it produced reached main, I want the work "
                + "itself delivered, so that done means the change is on main rather than meaning that a "
                + "row says so.");
        wishlist.setAcceptanceCriteria("Given the task named above, When this finding is delivered, Then a "
                + "merged pull request exists on main carrying the change that task was for, and it is "
                + "named here.");
        wishlist.setDod("BARCAN-TAG-00: the change the named task was for is present on main in a merged "
                + "pull request, and the task's status is no longer the only evidence of delivery.");
        wishlistRepository.save(wishlist);

        log.warn("DeliveryRealityProducerService: filed missing delivery of task {} ({}) as scope - status "
                + "said done, nothing reached main", task.getId(), title);
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
        for (TaskEntity task : taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())) {
            if (task.getStatus() != TaskStatus.done) {
                continue;
            }
            // Same two checks the dashboard applies - a DECISION-stage or `complex`-Cynefin task is never
            // expected to reach main on its own, and flagging it would be the 2026-07-25 false positive.
            if (readinessService.reachedMain(task) || readinessService.isAuxiliaryTask(task)) {
                continue;
            }
            if (!operationalRealityFindingRepository.findByTaskId(task.getId()).isEmpty()) {
                alreadyKnown += refreshStandingEvidence(task);
                continue;
            }
            OperationalRealityFindingEntity finding = new OperationalRealityFindingEntity();
            finding.setTaskId(task.getId());
            // Null since V103: a session is one KIND of record, not the essence of the claim, and this task
            // asserts completion having never had one.
            finding.setJulesSessionId(null);
            finding.setExpectedStatus(TaskStatus.done.name());
            finding.setActualGithubState(NO_MERGE_EVIDENCE);
            finding = operationalRealityFindingRepository.save(finding);

            EvidenceNodeEntity node = new EvidenceNodeEntity();
            node.setProjectId(project.getId());
            node.setFeatureId(task.getFeatureId());
            node.setPolarity(EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
            node.setSummaryText("Task " + task.getId() + " ("
                    + com.eneik.production.services.task.TaskTitleBuilder.displayTitle(task)
                    + ") reports status done while nothing ever reached main - no merge evidence exists for it. "
                    + "Its own status is the only thing asserting the work was delivered.");
            node.setOperationalRealityFindingId(finding.getId());
            evidenceNodeRepository.save(node);
            fileTheMissingWorkAsScope(project, task);
            recorded++;

            log.warn("DeliveryRealityProducerService: task {} in project {} reports done with no merge "
                            + "evidence - recorded as operational reality finding {}",
                    task.getId(), project.getName(), finding.getId());
        }
        if (recorded > 0 || alreadyKnown > 0) {
            log.info("DeliveryRealityProducerService: project {} - {} new done-without-merge finding(s), "
                    + "{} already recorded", project.getName(), recorded, alreadyKnown);
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
