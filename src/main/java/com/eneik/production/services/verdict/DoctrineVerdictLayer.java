package com.eneik.production.services.verdict;

import com.eneik.production.dto.dashboard.EmsDashboardMetricsDto;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.dashboard.EmsMetricsService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the thirteen BARCAN role doctrines say about the current project state.
 *
 * This layer already existed and nothing read it (F24). On 2026-08-15 it reported `statusLabel: blocked`
 * with the interpretation *"One or more BARCAN doctrines refuse the current project state; resolve Must-Be
 * objections before acceptance"* - not one of thirteen roles satisfied, two refusing outright, seven
 * objecting - while the task pipeline dispatched normally and reported 82% progress. A layer designed to
 * withhold acceptance was withholding it, and no other layer could hear.
 *
 * Nothing new is computed here. The stances are exactly the ones `EmsMetricsService` already derives; this
 * only expresses them in the one type every layer speaks, so they can finally be combined with the others
 * instead of sitting in a dashboard field.
 */
@Component
public class DoctrineVerdictLayer implements VerdictLayer {

    private final EmsMetricsService emsMetricsService;
    private final TaskRepository taskRepository;
    private final WishlistRepository wishlistRepository;

    public DoctrineVerdictLayer(EmsMetricsService emsMetricsService,
                                TaskRepository taskRepository,
                                WishlistRepository wishlistRepository) {
        this.emsMetricsService = emsMetricsService;
        this.taskRepository = taskRepository;
        this.wishlistRepository = wishlistRepository;
    }

    @Override
    public String layerName() {
        return "doctrine";
    }

    /**
     * Always declares at least one proposition. An empty domain would mean this layer contributes nothing
     * to the conjunction, and contributing nothing is indistinguishable from permitting - which is the
     * exact "silence read as approval" this whole structure exists to prevent.
     */
    static final String PROPOSITION_EVALUATED = "the doctrine layer has evaluated this project at all";

    @Override
    public List<String> declaredPropositions(UUID projectId) {
        List<String> propositions = new ArrayList<>();
        propositions.add(PROPOSITION_EVALUATED);
        for (var role : rolesOf(projectId)) {
            propositions.add(propositionFor(role.roleTag()));
        }
        return propositions;
    }

    @Override
    public List<Judgement> judge(UUID projectId) {
        List<Judgement> judgements = new ArrayList<>();
        var roles = rolesOf(projectId);
        if (roles.isEmpty()) {
            judgements.add(Judgement.abstain(layerName(), PROPOSITION_EVALUATED,
                    "no doctrine stances are available for this project"));
            return judgements;
        }
        judgements.add(Judgement.permit(layerName(), PROPOSITION_EVALUATED,
                roles.size() + " doctrine(s) evaluated"));
        for (var role : roles) {
            String proposition = propositionFor(role.roleTag());
            String stance = role.stance() == null ? "unknown" : role.stance();
            String objection = role.topObjection() == null ? "" : role.topObjection();
            String evidence = "stance=" + stance + " satisfaction=" + role.satisfactionScore()
                    + " confidence=" + role.confidence();

            switch (stance) {
                case "satisfied", "almost_satisfied" ->
                        judgements.add(Judgement.permit(layerName(), proposition, evidence));
                // Both are refusals for advancement purposes, on this layer's own stated terms: its
                // interpretation says Must-Be objections are to be resolved BEFORE acceptance, so an
                // outstanding objection is a withheld permission, not a note.
                case "refuses", "objects" ->
                        judgements.add(Judgement.withhold(layerName(), proposition,
                                objection.isBlank() ? stance : objection, evidence));
                default ->
                        judgements.add(Judgement.abstain(layerName(), proposition,
                                "this doctrine has no established stance on the current state"));
            }
        }
        return judgements;
    }

    private String propositionFor(String roleTag) {
        return "doctrine " + roleTag + " accepts the current project state";
    }

    private List<EmsDashboardMetricsDto.RoleDoctrineVerdict> rolesOf(UUID projectId) {
        try {
            EmsDashboardMetricsDto metrics = emsMetricsService.build(
                    taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId),
                    wishlistRepository.findByProjectId(projectId));
            if (metrics == null || metrics.roleDoctrineReadiness() == null
                    || metrics.roleDoctrineReadiness().roles() == null) {
                return List.of();
            }
            return metrics.roleDoctrineReadiness().roles();
        } catch (RuntimeException e) {
            // Deliberately NOT swallowed. VerdictReconciliation catches a throwing layer and records an
            // abstention against it, which keeps the failure inside the reckoning. Returning an empty list
            // here instead would make the layer contribute nothing at all - and under conjunction,
            // contributing nothing is indistinguishable from permitting.
            throw e;
        }
    }
}
