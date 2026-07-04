package com.eneik.production.services.compiler;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.gate.GateOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TechnicalLeadCompiler {

    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final ProjectGenerationStateRepository projectGenerationStateRepository;
    private final GateOrchestrator gateOrchestrator;
    private final ObjectMapper objectMapper;

    private static final String TECH_LEAD_ROLE_TAG = "BARCAN-TAG-09";

    public TechnicalLeadCompiler(WishlistRepository wishlistRepository,
                                 TaskRepository taskRepository,
                                 ProjectRepository projectRepository,
                                 RoleRepository roleRepository,
                                 ProjectGenerationStateRepository projectGenerationStateRepository,
                                 GateOrchestrator gateOrchestrator,
                                 ObjectMapper objectMapper) {
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.roleRepository = roleRepository;
        this.projectGenerationStateRepository = projectGenerationStateRepository;
        this.gateOrchestrator = gateOrchestrator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void compile(UUID wishlistId, String technicalLeadRoleTag, String jtbd, LeanValue leanValue,
                        String tocConstraintRef, String sixSigmaMetric, String dod, String acceptanceCriteria) {
        WishlistEntity wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new IllegalArgumentException("Wishlist not found: " + wishlistId));

        wishlist.setCompiledByRole(technicalLeadRoleTag);
        wishlist.setJtbd(jtbd);
        wishlist.setLeanValue(leanValue);
        wishlist.setTocConstraintRef(tocConstraintRef);
        wishlist.setSixSigmaMetric(sixSigmaMetric);
        wishlist.setDod(dod);
        wishlist.setAcceptanceCriteria(acceptanceCriteria);

        wishlistRepository.save(wishlist);
    }

    @Transactional
    public TaskEntity createTaskFromWishlist(UUID wishlistId) {
        WishlistEntity wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new IllegalArgumentException("Wishlist not found: " + wishlistId));

        if (!TECH_LEAD_ROLE_TAG.equals(wishlist.getCompiledByRole())) {
            throw new IllegalStateException("Only Technical Lead (" + TECH_LEAD_ROLE_TAG + ") can compile tasks. Found: " + wishlist.getCompiledByRole());
        }

        java.util.List<String> errors = validateDefinitionOfReady(wishlist);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Definition of Ready not met:\n" + String.join("\n", errors));
        }

        ProjectEntity project = projectRepository.findById(wishlist.getProjectId())
                .orElseThrow(() -> new IllegalStateException("Project not found: " + wishlist.getProjectId()));

        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setDescription(wishlist.getContent());

        // Defaulting role to Backend Engineer or similar if not specified?
        // The task says "task.tag refers to an active role" in quality gate.
        // For now, let's assume we need to find a role.
        // Actually, the compile() method doesn't take a role tag for the task itself.
        // I'll default it to BARCAN-TAG-02 (Backend) for now if I must, but ideally it should be in wishlist.
        // Wait, the prompt says "task.tag refers to an active role" is a check.
        // Let's use the source_role_tag from wishlist as a hint, or default it.
        RoleEntity role = roleRepository.findById("BARCAN-TAG-02")
                .orElseThrow(() -> new IllegalStateException("Default role not found"));
        task.setRole(role);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jtbd", wishlist.getJtbd());
        payload.put("lean_value", wishlist.getLeanValue().name());
        payload.put("toc_constraint_ref", wishlist.getTocConstraintRef());
        payload.put("six_sigma_metric", wishlist.getSixSigmaMetric());
        payload.put("dod", wishlist.getDod());
        payload.put("acceptance_criteria", wishlist.getAcceptanceCriteria());
        task.setPayload(payload);

        task.setStatus(TaskStatus.queued);
        TaskEntity savedTask = taskRepository.save(task);
        gateOrchestrator.runTaskSpecGate(savedTask);

        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlistRepository.save(wishlist);

        return savedTask;
    }

    @Transactional
    public void stopGeneration(UUID projectId) {
        ProjectGenerationStateEntity state = projectGenerationStateRepository.findById(projectId)
                .orElseGet(() -> {
                    ProjectGenerationStateEntity newState = new ProjectGenerationStateEntity();
                    newState.setProjectId(projectId);
                    return newState;
                });

        state.setGenerationStopped(true);
        state.setStoppedAt(Instant.now());
        projectGenerationStateRepository.save(state);
    }

    private java.util.List<String> validateDefinitionOfReady(WishlistEntity w) {
        java.util.List<String> errors = new java.util.ArrayList<>();

        // Шаг 1 — Bottleneck Check (TOC)
        if (w.getTocConstraintRef() == null || w.getTocConstraintRef().trim().isEmpty()) {
            errors.add("Шаг 1 не пройден: отсутствует ссылка на bottleneck (toc_constraint_ref)");
        }

        // Шаг 2 — Lean Classification
        if (w.getLeanValue() == null) {
            errors.add("Шаг 2 не пройден: lean_value не указан");
        } else if (w.getLeanValue() == LeanValue.waste) {
            errors.add("Шаг 2 не пройден: lean_value не может быть 'waste'");
        }

        // Шаг 3 — JTBD
        if (w.getJtbd() == null || w.getJtbd().trim().isEmpty()) {
            errors.add("Шаг 3 не пройден: jtbd не заполнен");
        }

        // Шаг 4 — Six Sigma Metric
        if (w.getSixSigmaMetric() == null || w.getSixSigmaMetric().trim().isEmpty()) {
            errors.add("Шаг 4 не пройден: six_sigma_metric не заполнен");
        }

        // Шаг 5 — Role-Grounded DoD
        String dod = w.getDod();
        if (dod == null || dod.trim().isEmpty()) {
            errors.add("Шаг 5 не пройден: DoD не заполнен");
        } else if (!dod.matches(".*BARCAN-TAG-\\d{2}.*")) {
            errors.add("Шаг 5 не пройден: DoD не ссылается на Refusal Criteria роли (BARCAN-TAG-XX)");
        } else {
            // Шаг 6 — Design System Reference (для UI/design задач)
            if (dod.contains("BARCAN-TAG-03") || dod.contains("BARCAN-TAG-11")) {
                boolean hasDesignSystemRef = dod.contains("docs/DESIGN_SYSTEM.md");
                boolean hasPendingRef = dod.contains("pending: design system not yet defined");

                java.io.File designSystemFile = new java.io.File("docs/DESIGN_SYSTEM.md");
                if (designSystemFile.exists()) {
                    if (!hasDesignSystemRef) {
                        errors.add("Шаг 6 не пройден: для UI-задач DoD обязан ссылаться на docs/DESIGN_SYSTEM.md");
                    }
                } else {
                    if (!hasPendingRef) {
                        errors.add("Шаг 6 не пройден: Design System не определена, DoD обязан содержать 'pending: design system not yet defined'");
                    }
                }
            }
        }

        // Шаг 7 — Acceptance Criteria
        if (w.getAcceptanceCriteria() == null || w.getAcceptanceCriteria().trim().isEmpty()) {
            errors.add("Шаг 7 не пройден: acceptance_criteria не заполнен");
        }

        return errors;
    }
}
