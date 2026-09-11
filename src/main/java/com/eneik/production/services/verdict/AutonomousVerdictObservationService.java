package com.eneik.production.services.verdict;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.service.DefectJournalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Autonomous reader of layer verdicts into {@link DefectJournalService}.
 *
 * Implements Prescription 8 (Section XVI §8) grounded in
 * {@code FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK} (D011 Perception failure).
 *
 * A signal is valid only if it carries semantic informational feedback that guides next action rather
 * than repetitive unthrottled noise. Prior to this reader, {@link VerdictReconciliation} was only queried
 * on-demand via HTTP or via the disabled {@link VerdictGate}.
 *
 * To prevent flooding the defect journal (~540 duplicate entries/hour from 9 doctrine role refusals on a
 * 60-second tick), this service enforces <b>delta-only recording</b>:
 * <ul>
 *   <li>A defect is recorded ONLY when a refusal is newly observed or its reason changes.</li>
 *   <li>Consecutive ticks with the identical refusal produce ZERO duplicate records.</li>
 *   <li>When a previously refusing proposition transitions back to {@link Verdict#PERMIT} or {@link Verdict#ABSTAIN},
 *       it is cleared from the active ledger, ensuring any subsequent regression will be recorded anew.</li>
 * </ul>
 */
@Service
public class AutonomousVerdictObservationService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousVerdictObservationService.class);

    public record RefusalKey(UUID projectId, String layer, String proposition) {}

    private final VerdictReconciliation reconciliation;
    private final DefectJournalService defectJournalService;

    // In-memory ledger of active refusals: (projectId, layer, proposition) -> reason
    private final Map<RefusalKey, String> activeRefusals = new ConcurrentHashMap<>();

    // Cadence tracking per project
    private final Map<UUID, AtomicLong> projectTickCounters = new ConcurrentHashMap<>();

    @Value("${verdict.observation.cadence-ticks:1}")
    private int cadenceTicks = 1;

    public AutonomousVerdictObservationService(VerdictReconciliation reconciliation,
                                              DefectJournalService defectJournalService) {
        this(reconciliation, defectJournalService, 1);
    }

    public AutonomousVerdictObservationService(VerdictReconciliation reconciliation,
                                              DefectJournalService defectJournalService,
                                              int cadenceTicks) {
        this.reconciliation = reconciliation;
        this.defectJournalService = defectJournalService;
        this.cadenceTicks = Math.max(1, cadenceTicks);
    }

    /**
     * Observes the composite verdict for a project and records any new or changed refusals.
     *
     * @param projectId project to observe
     * @return list of newly recorded defect journal entities on this tick (empty if no change, throttled, or permit)
     */
    public List<DefectJournalEntity> observe(UUID projectId) {
        return observe(projectId, false);
    }

    /**
     * Observes the composite verdict for a project with optional force flag to bypass cadence check.
     */
    public List<DefectJournalEntity> observe(UUID projectId, boolean force) {
        if (projectId == null || reconciliation == null || defectJournalService == null) {
            return List.of();
        }

        if (!force && cadenceTicks > 1) {
            long tick = projectTickCounters.computeIfAbsent(projectId, k -> new AtomicLong(0)).incrementAndGet();
            if (tick % cadenceTicks != 1) {
                log.debug("Verdict observation skipped for project {} (cadence tick {} / {})",
                        projectId, tick, cadenceTicks);
                return List.of();
            }
        }

        VerdictReconciliation.Reconciliation r;
        try {
            r = reconciliation.reconcile(projectId);
        } catch (Exception e) {
            log.warn("Verdict observation: reconciliation failed for project {}: {}", projectId, e.getMessage());
            return List.of();
        }

        if (r == null || r.judgements() == null) {
            return List.of();
        }

        List<DefectJournalEntity> recorded = new ArrayList<>();
        Set<RefusalKey> currentRefusals = new HashSet<>();

        for (Judgement j : r.judgements()) {
            if (j == null) {
                continue;
            }

            if (j.verdict() == Verdict.WITHHOLD) {
                String layer = j.layer() == null ? "unknown_layer" : j.layer();
                String proposition = j.proposition() == null ? "unnamed_proposition" : j.proposition();
                String reason = j.reason() == null ? "" : j.reason().trim();

                RefusalKey key = new RefusalKey(projectId, layer, proposition);
                currentRefusals.add(key);

                String previousReason = activeRefusals.get(key);
                if (previousReason == null) {
                    // State transition 1: New refusal
                    DefectJournalEntity entity = recordRefusalDefect(projectId, j, reason, false, null);
                    if (entity != null) {
                        activeRefusals.put(key, reason);
                        recorded.add(entity);
                        log.info("Verdict refusal recorded for project {} [{} / {}]: {}",
                                projectId, layer, proposition, reason);
                    }
                } else if (!Objects.equals(previousReason, reason)) {
                    // State transition 2: Refusal reason changed
                    DefectJournalEntity entity = recordRefusalDefect(projectId, j, reason, true, previousReason);
                    if (entity != null) {
                        activeRefusals.put(key, reason);
                        recorded.add(entity);
                        log.info("Verdict refusal reason changed for project {} [{} / {}]: '{}' -> '{}'",
                                projectId, layer, proposition, previousReason, reason);
                    }
                } else {
                    // Suppressed: identical refusal on consecutive ticks (FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK)
                    log.debug("Suppressing duplicate verdict refusal for project {} [{} / {}]",
                            projectId, layer, proposition);
                }
            }
        }

        // State transition 3: Cleared refusal (proposition transitioned to PERMIT/ABSTAIN or disappeared)
        activeRefusals.keySet().removeIf(key -> {
            if (key.projectId().equals(projectId) && !currentRefusals.contains(key)) {
                log.info("Verdict refusal cleared for project {} [{} / {}]",
                        projectId, key.layer(), key.proposition());
                return true;
            }
            return false;
        });

        return List.copyOf(recorded);
    }

    private DefectJournalEntity recordRefusalDefect(UUID projectId, Judgement j, String currentReason,
                                                   boolean reasonChanged, String previousReason) {
        try {
            String layer = j.layer() != null && !j.layer().isBlank() ? j.layer() : "verdict";
            String defectType = sanitizeDefectType(layer);
            String description = buildDescription(j, currentReason, reasonChanged, previousReason);

            return defectJournalService.recordDefect(
                    projectId,
                    "HIGH",
                    "LAYER_VERDICT",
                    layer,
                    defectType,
                    description,
                    1.0
            );
        } catch (Exception e) {
            log.error("Failed to record verdict defect in DefectJournalService for project {}: {}",
                    projectId, e.getMessage(), e);
            return null;
        }
    }

    static String sanitizeDefectType(String layer) {
        String clean = layer.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return clean.endsWith("_REFUSAL") ? clean : clean + "_REFUSAL";
    }

    static String buildDescription(Judgement j, String currentReason, boolean reasonChanged, String previousReason) {
        StringBuilder sb = new StringBuilder();
        sb.append(j.proposition() != null && !j.proposition().isBlank() ? j.proposition() : "(unnamed proposition)");
        sb.append(": ");
        sb.append(!currentReason.isBlank() ? currentReason : "Refused without detailed reason");
        if (reasonChanged) {
            sb.append(" (reason updated, was: '");
            sb.append(previousReason != null && !previousReason.isBlank() ? previousReason : "empty");
            sb.append("')");
        }
        return sb.toString();
    }

    /**
     * Returns an unmodifiable snapshot of currently tracked active refusals.
     */
    public Map<RefusalKey, String> getActiveRefusals() {
        return Map.copyOf(activeRefusals);
    }

    /**
     * Clears tracked refusals (primarily for test isolation).
     */
    public void clearRefusals() {
        activeRefusals.clear();
        projectTickCounters.clear();
    }
}
