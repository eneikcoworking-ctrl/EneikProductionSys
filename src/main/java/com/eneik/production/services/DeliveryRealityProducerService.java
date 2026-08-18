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

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final OperationalRealityFindingRepository operationalRealityFindingRepository;
    private final EvidenceNodeRepository evidenceNodeRepository;

    public DeliveryRealityProducerService(ProjectRepository projectRepository,
                                          TaskRepository taskRepository,
                                          ClientDeliverableReadinessService readinessService,
                                          OperationalRealityFindingRepository operationalRealityFindingRepository,
                                          EvidenceNodeRepository evidenceNodeRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.readinessService = readinessService;
        this.operationalRealityFindingRepository = operationalRealityFindingRepository;
        this.evidenceNodeRepository = evidenceNodeRepository;
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
}
