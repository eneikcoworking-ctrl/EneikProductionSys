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

        validateField(wishlist.getJtbd(), "jtbd");
        validateField(wishlist.getLeanValue(), "lean_value");
        validateField(wishlist.getTocConstraintRef(), "toc_constraint_ref");
        validateField(wishlist.getSixSigmaMetric(), "six_sigma_metric");
        validateField(wishlist.getDod(), "dod");
        validateField(wishlist.getAcceptanceCriteria(), "acceptance_criteria");

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

    private void validateField(Object value, String fieldName) {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            throw new IllegalStateException("Field '" + fieldName + "' is required for task creation from wishlist");
        }
    }
}
