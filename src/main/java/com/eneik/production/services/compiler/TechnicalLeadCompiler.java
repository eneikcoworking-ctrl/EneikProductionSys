package com.eneik.production.services.compiler;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.BottleneckAwarePriorityService;
import com.eneik.production.services.gate.GateOrchestrator;
import com.eneik.production.services.task.TaskTitleBuilder;
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
    private final BottleneckAwarePriorityService bottleneckAwarePriorityService;
    private final ObjectMapper objectMapper;
    private final ProjectHotspotFileRepository projectHotspotFileRepository;

    private static final String TECH_LEAD_ROLE_TAG = "BARCAN-TAG-09";

    public TechnicalLeadCompiler(WishlistRepository wishlistRepository,
                                 TaskRepository taskRepository,
                                 ProjectRepository projectRepository,
                                 RoleRepository roleRepository,
                                 ProjectGenerationStateRepository projectGenerationStateRepository,
                                 GateOrchestrator gateOrchestrator,
                                 BottleneckAwarePriorityService bottleneckAwarePriorityService,
                                 ObjectMapper objectMapper,
                                 ProjectHotspotFileRepository projectHotspotFileRepository) {
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.roleRepository = roleRepository;
        this.projectGenerationStateRepository = projectGenerationStateRepository;
        this.gateOrchestrator = gateOrchestrator;
        this.bottleneckAwarePriorityService = bottleneckAwarePriorityService;
        this.objectMapper = objectMapper;
        this.projectHotspotFileRepository = projectHotspotFileRepository;
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
        return createTaskFromWishlist(wishlistId, null, null, 0, 0, "standalone");
    }

    @Transactional
    public TaskEntity createTaskFromWishlist(UUID wishlistId,
                                             TaskEntity dependsOn,
                                             String emsGraphKey,
                                             int graphOrder,
                                             int graphSize,
                                             String graphEdgeReason) {
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

        if (project.getStatus() != ProjectStatus.active) {
            throw new IllegalStateException("project is frozen, task compilation paused until reactivated");
        }

        ProjectGenerationStateEntity generationState = projectGenerationStateRepository.findById(project.getId()).orElse(null);
        if (generationState != null && generationState.isGenerationStopped()) {
            throw new IllegalStateException("Generation is stopped for this project");
        }

        String ownerRole = targetRoleForWishlist(wishlist);
        String atomicGoal = roleAtomicGoal(wishlist, ownerRole);
        String semanticKey = semanticKey(project.getId(), ownerRole, wishlist, atomicGoal);

        if (wishlist.getStatus() == WishlistStatus.converted_to_task) {
            return findExistingSemanticTask(project.getId(), semanticKey)
                    .or(() -> taskRepository.findByProjectIdAndDescription(project.getId(), wishlist.getContent()))
                    .orElseGet(() -> taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream().findFirst().orElse(null));
        }

        java.util.Optional<TaskEntity> duplicate = findExistingSemanticTask(project.getId(), semanticKey);
        if (duplicate.isPresent()) {
            wishlist.setStatus(WishlistStatus.converted_to_task);
            wishlistRepository.save(wishlist);
            return duplicate.get();
        }

        java.util.List<TaskEntity> createdTasks = new java.util.ArrayList<>();
        boolean isIntegrationTask = "BARCAN-TAG-00".equals(ownerRole);
        createAndSaveTask(project, wishlist, ownerRole,
                atomicGoal,
                roleSpecificDod(ownerRole, isRecoveryWork(wishlist)),
                dependsOn,
                isIntegrationTask,
                false,
                createdTasks,
                new EmsTaskMetadata(semanticKey, emsGraphKey, graphOrder, graphSize, graphEdgeReason));

        // Atomic update of wishlist status after task creation
        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlistRepository.save(wishlist);

        // Run task spec gate on all created tasks
        for (TaskEntity createdTask : createdTasks) {
            gateOrchestrator.runTaskSpecGate(createdTask);
        }

        return createdTasks.get(0);
    }

    private boolean hasUiComponent(WishlistEntity wishlist) {
        String content = wishlist.getContent();
        String jtbd = wishlist.getJtbd();
        String lower = ((content != null ? content : "") + " " + (jtbd != null ? jtbd : "")).toLowerCase(java.util.Locale.ROOT);
        return lower.contains("3d") || lower.contains("дизайн") || lower.contains("интерфейс") || 
               lower.contains("сцена") || lower.contains("управление") || lower.contains("экран") || 
               lower.contains("ui") || lower.contains("ux") || lower.contains("frontend") || 
               lower.contains("button") || lower.contains("click") || lower.contains("view") || 
               lower.contains("render") || lower.contains("figma") || lower.contains("дизайнер") || 
               lower.contains("дизайну") || lower.contains("фронтенд") || lower.contains("css");
    }

    private TaskEntity createAndSaveTask(ProjectEntity project, WishlistEntity wishlist, String roleTag,
                                         String atomicGoal, String dod, TaskEntity dependsOn,
                                         boolean isIntegrationTask, boolean hasIntegrationTask,
                                         java.util.List<TaskEntity> createdTasks,
                                         EmsTaskMetadata emsMetadata) {
        TaskEntity task = new TaskEntity();
        task.setProject(project);

        java.util.Set<String> extractedRoles = extractRoleTags(wishlist);
        if (extractedRoles.isEmpty() && roleTag != null) {
            extractedRoles.add(roleTag);
        }
        String joinedRoles = String.join(", ", extractedRoles);

        String cynefin = cynefinDomain(wishlist);
        String kano = kanoClass(wishlist, extractedRoles);
        String shortTitle = TaskTitleBuilder.build(roleTag, taskTitleSource(wishlist, atomicGoal));
        task.setTitle(shortTitle);
        task.setDescription(buildTaskDescription(wishlist, roleTag, atomicGoal, dod, kano, cynefin));

        RoleEntity role = roleRepository.findById(roleTag)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleTag));
        task.setRole(role);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("source_wishlist_id", wishlist.getId().toString());
        payload.put("source_role_tag", joinedRoles);
        payload.put("short_title", shortTitle);
        payload.put("slice_title", sliceTitle(wishlist));
        payload.put("role_atomic_goal", atomicGoal);
        payload.put("jtbd", englishMetadata(wishlist.getJtbd(), fallbackJtbd(wishlist)));
        payload.put("lean_value", wishlist.getLeanValue().name());
        payload.put("kano_class", kano);
        payload.put("cynefin_domain", cynefin);
        payload.put("toc_constraint_ref", wishlist.getTocConstraintRef());
        payload.put("six_sigma_metric", wishlist.getSixSigmaMetric());
        payload.put("dod", dod);
        payload.put("acceptance_criteria", englishMetadata(wishlist.getAcceptanceCriteria(), fallbackAcceptanceCriteria(wishlist)));
        payload.put("ems_management_system", "Eneik Management System");
        payload.put("ems_semantic_key", emsMetadata.semanticKey());
        payload.put("ems_graph_key", emsMetadata.graphKey() == null ? "" : emsMetadata.graphKey());
        payload.put("ems_graph_order", emsMetadata.graphOrder());
        payload.put("ems_graph_size", emsMetadata.graphSize());
        payload.put("ems_graph_edge_reason", emsMetadata.edgeReason() == null ? "" : emsMetadata.edgeReason());
        payload.put("ems_flow_stage", flowStage(roleTag));
        payload.put("ems_defect_work", isDefectWork(wishlist));
        payload.put("ems_defect_weight", defectWeight(wishlist, roleTag, cynefin));
        payload.put("ems_kpi_weight", kpiWeight(wishlist, kano, cynefin, extractedRoles));
        double criticality = criticalityScore(wishlist, roleTag, kano, cynefin, extractedRoles);
        payload.put("ems_criticality_score", criticality);
        payload.put("depends_on", dependsOn != null ? dependsOn.getId().toString() : "");
        payload.set("impact_coefficients", createImpactMatrix(roleTag, extractedRoles));
        task.setPayload(payload);

        task.setStatus(TaskStatus.queued);
        task.setCynefinDomain(cynefin);
        task.setSourceWishlistId(wishlist.getId());

        int priority = bottleneckAwarePriorityService.computePriority(wishlist.getTocConstraintRef());
        if (wishlist.getSourceRoleTag() != null && !wishlist.getSourceRoleTag().isBlank()) {
            priority = Math.max(priority, (int) Math.round(criticality * 10.0));
        }
        if ("chaotic".equalsIgnoreCase(cynefin)) {
            priority = 1000;
        }
        task.setPriority(priority);
        task.setDependsOn(dependsOn);
        task.setFileScope(determineFileScope(project, roleTag, fileScopeSource(wishlist), isIntegrationTask, hasIntegrationTask));
        
        TaskEntity saved = taskRepository.save(task);
        createdTasks.add(saved);
        return saved;
    }

    private ObjectNode createImpactMatrix(String ownerRole, java.util.Set<String> affectedRoles) {
        ObjectNode matrix = objectMapper.createObjectNode();
        java.util.Set<String> roles = new java.util.HashSet<>(affectedRoles);
        roles.add(ownerRole);

        for (String tag : java.util.List.of("BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-02", "BARCAN-TAG-03",
                "BARCAN-TAG-04", "BARCAN-TAG-05", "BARCAN-TAG-06", "BARCAN-TAG-07",
                "BARCAN-TAG-08", "BARCAN-TAG-09", "BARCAN-TAG-10", "BARCAN-TAG-11")) {
            double coefficient = 0.1;
            if (tag.equals(ownerRole)) {
                coefficient = 0.9;
            } else if (roles.contains(tag)) {
                coefficient = 0.8;
            }
            matrix.put(tag, coefficient);
        }
        return matrix;
    }

    private java.util.Set<String> extractRoleTags(WishlistEntity wishlist) {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        if (wishlist.getSourceRoleTag() != null && !wishlist.getSourceRoleTag().isBlank()) {
            tags.add(wishlist.getSourceRoleTag().trim());
        }
        String searchArea = (wishlist.getContent() != null ? wishlist.getContent() : "") + " "
                + (wishlist.getDod() != null ? wishlist.getDod() : "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("BARCAN-TAG-\\d{2}").matcher(searchArea);
        while (matcher.find()) {
            tags.add(matcher.group());
        }
        return tags;
    }

    private String taskTitleSource(WishlistEntity wishlist, String atomicGoal) {
        return (sliceTitle(wishlist) + " "
                + (atomicGoal != null ? atomicGoal : "") + " "
                + (wishlist.getJtbd() != null ? wishlist.getJtbd() : "") + " "
                + (wishlist.getContent() != null ? wishlist.getContent() : "") + " "
                + (wishlist.getAcceptanceCriteria() != null ? wishlist.getAcceptanceCriteria() : "")).trim();
    }

    private java.util.Optional<TaskEntity> findExistingSemanticTask(UUID projectId, String semanticKey) {
        if (semanticKey == null || semanticKey.isBlank()) {
            return java.util.Optional.empty();
        }
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(task -> task.getPayload() != null)
                .filter(task -> semanticKey.equals(task.getPayload().path("ems_semantic_key").asText("")))
                .findFirst();
    }

    private String semanticKey(UUID projectId, String roleTag, WishlistEntity wishlist, String atomicGoal) {
        String source = projectId + "|" + roleTag + "|"
                + normalizeSemanticText(wishlist.getJtbd()) + "|"
                + normalizeSemanticText(wishlist.getAcceptanceCriteria()) + "|"
                + normalizeSemanticText(atomicGoal);
        return "ems:" + sha256(source).substring(0, 24);
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String normalizeSemanticText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String flowStage(String roleTag) {
        return switch (roleTag) {
            case "BARCAN-TAG-09" -> "decision";
            case "BARCAN-TAG-01" -> "architecture";
            case "BARCAN-TAG-02", "BARCAN-TAG-04", "BARCAN-TAG-07", "BARCAN-TAG-08" -> "implementation";
            case "BARCAN-TAG-03", "BARCAN-TAG-11" -> "experience";
            case "BARCAN-TAG-05" -> "operations";
            case "BARCAN-TAG-06" -> "verification";
            case "BARCAN-TAG-00" -> "integration";
            case "BARCAN-TAG-10" -> "compliance";
            default -> "implementation";
        };
    }

    private boolean isDefectWork(WishlistEntity wishlist) {
        if (isRecoveryWork(wishlist)) {
            return true;
        }
        String text = ((wishlist.getContent() != null ? wishlist.getContent() : "") + " "
                + (wishlist.getTocConstraintRef() != null ? wishlist.getTocConstraintRef() : "") + " "
                + (wishlist.getAcceptanceCriteria() != null ? wishlist.getAcceptanceCriteria() : ""))
                .toLowerCase(java.util.Locale.ROOT);
        return text.contains("defect")
                || text.contains("bug")
                || text.contains("blocker")
                || text.contains("failure")
                || text.contains("failed")
                || text.contains("regression")
                || text.contains("circuit breaker")
                || text.contains("generated artifact");
    }

    private double defectWeight(WishlistEntity wishlist, String roleTag, String cynefin) {
        double weight = isDefectWork(wishlist) ? 1.0 : 0.0;
        if ("BARCAN-TAG-06".equals(roleTag) || "BARCAN-TAG-00".equals(roleTag)) {
            weight += 0.25;
        }
        if ("chaotic".equalsIgnoreCase(cynefin) || "complex".equalsIgnoreCase(cynefin)) {
            weight += 0.25;
        }
        return round(Math.min(1.5, weight));
    }

    private double kpiWeight(WishlistEntity wishlist, String kano, String cynefin, java.util.Set<String> extractedRoles) {
        double weight = switch (kano == null ? "" : kano.toLowerCase(java.util.Locale.ROOT)) {
            case "must-be", "mustbe" -> 1.25;
            case "performance", "one-dimensional" -> 1.1;
            case "attractive" -> 0.9;
            default -> 1.0;
        };
        if (extractedRoles.size() > 1) {
            weight = Math.max(weight, 1.5);
        }
        if ("complex".equalsIgnoreCase(cynefin)) {
            weight += 0.15;
        } else if ("chaotic".equalsIgnoreCase(cynefin)) {
            weight += 0.35;
        }
        if (isDefectWork(wishlist)) {
            weight += 0.2;
        }
        if (wishlist.getSourceRoleTag() != null && !wishlist.getSourceRoleTag().isBlank()) {
            weight += doctrinePressure(wishlist.getSourceRoleTag());
        }
        return round(weight);
    }

    private double criticalityScore(WishlistEntity wishlist, String roleTag, String kano, String cynefin, java.util.Set<String> extractedRoles) {
        double score = kpiWeight(wishlist, kano, cynefin, extractedRoles) * 10.0;
        score += defectWeight(wishlist, roleTag, cynefin) * 5.0;
        if ("BARCAN-TAG-00".equals(roleTag) || "BARCAN-TAG-06".equals(roleTag) || "BARCAN-TAG-07".equals(roleTag)) {
            score += 2.0;
        }
        if (wishlist.getSourceRoleTag() != null && !wishlist.getSourceRoleTag().isBlank()) {
            score += doctrinePressure(wishlist.getSourceRoleTag()) * 8.0;
        }
        if (extractedRoles.size() > 1) {
            score += 10.0;
        }
        return round(score);
    }

    private double doctrinePressure(String sourceRoleTag) {
        if (sourceRoleTag == null) {
            return 0.0;
        }
        if (sourceRoleTag.contains(",")) {
            double maxPressure = 0.0;
            for (String tag : sourceRoleTag.split(",")) {
                maxPressure = Math.max(maxPressure, singleDoctrinePressure(tag.trim()));
            }
            return maxPressure;
        }
        return singleDoctrinePressure(sourceRoleTag);
    }

    private double singleDoctrinePressure(String sourceRoleTag) {
        return switch (sourceRoleTag) {
            case "BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-02", "BARCAN-TAG-05",
                    "BARCAN-TAG-06", "BARCAN-TAG-07", "BARCAN-TAG-08", "BARCAN-TAG-10" -> 0.25;
            case "BARCAN-TAG-09" -> 0.20;
            case "BARCAN-TAG-03", "BARCAN-TAG-04", "BARCAN-TAG-11" -> 0.15;
            default -> 0.10;
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record EmsTaskMetadata(
            String semanticKey,
            String graphKey,
            int graphOrder,
            int graphSize,
            String edgeReason
    ) {}

    private String determineFileScope(ProjectEntity project, String roleTag, String wishContent, boolean isIntegrationTask, boolean hasIntegrationTask) {
        java.util.List<String> paths = new java.util.ArrayList<>();

        // 1. If this is the integration task (isIntegrationTask) OR if it is Frontend (TAG-11) and there is no integration task,
        // we add all hotspot files of the project.
        if (isIntegrationTask || ("BARCAN-TAG-11".equals(roleTag) && !hasIntegrationTask)) {
            try {
                java.util.List<com.eneik.production.models.persistence.ProjectHotspotFileEntity> hotspots = projectHotspotFileRepository.findByProjectId(project.getId());
                for (com.eneik.production.models.persistence.ProjectHotspotFileEntity hotspot : hotspots) {
                    paths.add(hotspot.getFilePath());
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // 2. Search existing files in workspace matching keywords
        String workspacePath = project.getWorkspacePath();
        java.io.File workspaceDir = null;
        if (workspacePath != null && !workspacePath.trim().isEmpty()) {
            workspaceDir = new java.io.File(workspacePath);
        }
        if (workspaceDir == null || !workspaceDir.exists()) {
            workspaceDir = new java.io.File(".");
        }

        java.util.List<String> keywords = new java.util.ArrayList<>();
        String lowerWish = wishContent != null ? wishContent.toLowerCase(java.util.Locale.ROOT) : "";
        if (lowerWish.contains("chess") || lowerWish.contains("шахмат")) {
            keywords.add("chess");
            keywords.add("game");
            keywords.add("engine");
        }
        if (lowerWish.contains("board") || lowerWish.contains("доск")) {
            keywords.add("board");
        }
        String[] words = lowerWish.split("[^a-zA-Z0-9а-яА-Я]+");
        java.util.Set<String> stopWords = java.util.Set.of(
            "with", "from", "that", "this", "recommendation", "based", "task", "completion",
            "kano", "refactoring", "description", "required", "cleanup", "рекомендация"
        );
        for (String w : words) {
            if (w.length() >= 4 && !stopWords.contains(w)) {
                keywords.add(w);
            }
        }

        java.util.List<java.io.File> foundFiles = new java.util.ArrayList<>();
        if (workspaceDir.exists()) {
            findFilesWithName(workspaceDir, keywords, foundFiles);
        }

        java.util.List<String> relevantPaths = new java.util.ArrayList<>();
        for (java.io.File f : foundFiles) {
            String rel = getRelativePath(workspaceDir, f);
            if (isRelevantForRole(roleTag, rel)) {
                relevantPaths.add(rel);
            }
        }

        // Add found paths to the paths list
        paths.addAll(relevantPaths);

        // 3. If no relevant files found, determine the predicted new path
        if (relevantPaths.isEmpty()) {
            String featureName = getFeatureName(wishContent);

            // Detect stack based on workspace contents
            boolean isNextJsOrReact = false;
            if (workspaceDir != null && workspaceDir.exists()) {
                isNextJsOrReact = new java.io.File(workspaceDir, "next.config.ts").exists() ||
                                  new java.io.File(workspaceDir, "next.config.js").exists() ||
                                  new java.io.File(workspaceDir, "pnpm-workspace.yaml").exists() ||
                                  new java.io.File(workspaceDir, "package.json").exists() && !new java.io.File(workspaceDir, "pom.xml").exists();
            }

            if (isNextJsOrReact) {
                // Next.js/React structure fallback
                if ("BARCAN-TAG-01".equals(roleTag)) {
                    paths.add("docs/architecture/" + featureName.toLowerCase(java.util.Locale.ROOT) + ".md");
                } else if ("BARCAN-TAG-02".equals(roleTag)) { // Backend
                    paths.add("src/app/api/" + featureName.toLowerCase(java.util.Locale.ROOT) + "/route.ts");
                    paths.add("prisma/schema.prisma");
                } else if ("BARCAN-TAG-03".equals(roleTag)) { // Design
                    paths.add("src/components/landing/" + featureName + ".tsx");
                    paths.add("src/app/globals.css");
                } else if ("BARCAN-TAG-04".equals(roleTag)) {
                    paths.add("src/lib/ai/" + featureName.toLowerCase(java.util.Locale.ROOT) + ".ts");
                } else if ("BARCAN-TAG-11".equals(roleTag)) { // Frontend
                    paths.add("src/app/" + featureName.toLowerCase(java.util.Locale.ROOT) + "/page.tsx");
                    paths.add("src/components/landing/" + featureName + ".tsx");
                } else if ("BARCAN-TAG-05".equals(roleTag)) {
                    paths.add("Dockerfile");
                    paths.add(".github/workflows/ci.yml");
                } else if ("BARCAN-TAG-06".equals(roleTag)) { // QA
                    paths.add("src/__tests__/" + featureName.toLowerCase(java.util.Locale.ROOT) + ".test.ts");
                } else if ("BARCAN-TAG-07".equals(roleTag)) {
                    paths.add("src/lib/security/" + featureName.toLowerCase(java.util.Locale.ROOT) + ".ts");
                } else if ("BARCAN-TAG-08".equals(roleTag)) {
                    paths.add("prisma/schema.prisma");
                    paths.add("src/lib/data/" + featureName.toLowerCase(java.util.Locale.ROOT) + ".ts");
                } else if ("BARCAN-TAG-00".equals(roleTag)) { // Code Guardian / Integration Task
                    paths.add("src/app/layout.tsx");
                } else if ("BARCAN-TAG-09".equals(roleTag)) {
                    paths.add("docs/delivery/" + featureName.toLowerCase(java.util.Locale.ROOT) + ".md");
                } else if ("BARCAN-TAG-10".equals(roleTag)) {
                    paths.add("docs/compliance/" + featureName.toLowerCase(java.util.Locale.ROOT) + ".md");
                }
            } else {
                // Default Java Spring Boot structure fallback
                if ("BARCAN-TAG-01".equals(roleTag)) {
                    paths.add("docs/architecture/" + featureName + ".md");
                } else if ("BARCAN-TAG-02".equals(roleTag)) { // Backend
                    if ("Chess".equals(featureName) || "ChessAi".equals(featureName)) {
                        paths.add("src/main/java/com/eneik/production/services/ChessService.java");
                        paths.add("src/main/java/com/eneik/production/services/ChessEngine.java");
                    } else {
                        paths.add("src/main/java/com/eneik/production/services/" + featureName + "Service.java");
                    }
                } else if ("BARCAN-TAG-03".equals(roleTag)) { // Design
                    if ("Chess".equals(featureName) || "ChessAi".equals(featureName)) {
                        paths.add("frontend/src/components/ChessBoard.svelte");
                    } else {
                        paths.add("frontend/src/components/" + featureName + ".svelte");
                    }
                } else if ("BARCAN-TAG-04".equals(roleTag)) {
                    paths.add("src/main/java/com/eneik/production/services/" + featureName + "AiService.java");
                    paths.add("src/models/ml/" + featureName + "Model.py");
                } else if ("BARCAN-TAG-11".equals(roleTag)) { // Frontend
                    if ("Chess".equals(featureName) || "ChessAi".equals(featureName)) {
                        paths.add("frontend/src/components/ChessBoard.svelte");
                    } else {
                        paths.add("frontend/src/components/" + featureName + ".svelte");
                    }
                } else if ("BARCAN-TAG-05".equals(roleTag)) {
                    paths.add("Dockerfile.backend");
                    paths.add("docker-compose.yml");
                    paths.add(".github/workflows/ci.yml");
                } else if ("BARCAN-TAG-06".equals(roleTag)) { // QA
                    if ("Chess".equals(featureName) || "ChessAi".equals(featureName)) {
                        paths.add("src/test/java/com/eneik/production/services/ChessServiceTest.java");
                    } else {
                        paths.add("src/test/java/com/eneik/production/services/" + featureName + "ServiceTest.java");
                    }
                } else if ("BARCAN-TAG-07".equals(roleTag)) {
                    paths.add("src/main/java/com/eneik/production/services/security/" + featureName + "SecurityService.java");
                } else if ("BARCAN-TAG-08".equals(roleTag)) {
                    paths.add("src/main/resources/db/migration/V_NEXT__" + featureName.toLowerCase(java.util.Locale.ROOT) + ".sql");
                    paths.add("src/main/java/com/eneik/production/models/persistence/" + featureName + "Entity.java");
                } else if ("BARCAN-TAG-00".equals(roleTag)) { // Code Guardian / Integration Task
                    paths.add("src/main/java/com/eneik/production/services/" + featureName + "IntegrationService.java");
                } else if ("BARCAN-TAG-09".equals(roleTag)) {
                    paths.add("docs/delivery/" + featureName + ".md");
                } else if ("BARCAN-TAG-10".equals(roleTag)) {
                    paths.add("docs/compliance/" + featureName + ".md");
                }
            }
        }

        // Deduplicate paths and strictly enforce file scope boundaries based on role relevance
        java.util.List<String> deduped = new java.util.ArrayList<>();
        for (String p : paths) {
            if ("BARCAN-TAG-00".equals(roleTag) || "BARCAN-TAG-01".equals(roleTag)
                    || "BARCAN-TAG-05".equals(roleTag) || "BARCAN-TAG-09".equals(roleTag)
                    || "BARCAN-TAG-10".equals(roleTag) || isRelevantForRole(roleTag, p)) {
                if (!deduped.contains(p)) {
                    deduped.add(p);
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(deduped);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void findFilesWithName(java.io.File dir, java.util.List<String> keywords, java.util.List<java.io.File> foundFiles) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File file : files) {
            String name = file.getName();
            if (file.isDirectory()) {
                if (name.equals(".git") || name.equals("node_modules") || name.equals("target") || name.equals("bin") || name.equals("project-workspaces")) {
                    continue;
                }
                findFilesWithName(file, keywords, foundFiles);
            } else {
                String lowerName = name.toLowerCase(java.util.Locale.ROOT);
                for (String kw : keywords) {
                    if (keywordMatches(lowerName, kw)) {
                        foundFiles.add(file);
                        break;
                    }
                }
            }
        }
    }

    private boolean keywordMatches(String lowerName, String kw) {
        if (!lowerName.contains(kw)) {
            return false;
        }
        if ("board".equals(kw) && lowerName.contains("dashboard")) {
            int index = 0;
            while ((index = lowerName.indexOf("board", index)) != -1) {
                if (index < 4 || !lowerName.substring(index - 4, index).equals("dash")) {
                    return true;
                }
                index += 5;
            }
            return false;
        }
        return true;
    }

    private String getRelativePath(java.io.File base, java.io.File file) {
        String basePath = base.getAbsolutePath().replace("\\", "/");
        String filePath = file.getAbsolutePath().replace("\\", "/");
        if (filePath.startsWith(basePath)) {
            String rel = filePath.substring(basePath.length());
            if (rel.startsWith("/")) {
                rel = rel.substring(1);
            }
            return rel;
        }
        return filePath;
    }

    private boolean isRelevantForRole(String roleTag, String relPath) {
        String lower = relPath.toLowerCase(java.util.Locale.ROOT);

        // Exclude lock files, configs, and ignore files from regular role assignment
        if (lower.endsWith("-lock.json") || lower.endsWith("package-lock.json") || lower.endsWith("pnpm-lock.yaml") ||
            lower.endsWith(".gitignore") || lower.endsWith(".env.example") || lower.endsWith(".npmrc")) {
            return false;
        }

        if ("BARCAN-TAG-01".equals(roleTag)) {
            return lower.endsWith(".md") || lower.contains("architecture") || lower.contains("adr");
        } else if ("BARCAN-TAG-02".equals(roleTag)) { // Backend
            // For Java Spring Boot
            if (lower.endsWith(".java") && !lower.contains("/test/")) {
                return true;
            }
            // For Node.js / Next.js / FastAPI
            if ((lower.contains("api/") || lower.contains("/api") || lower.contains("server") || lower.endsWith(".prisma") || lower.endsWith(".py")) &&
                !lower.contains("test") && !lower.endsWith(".svelte") && !lower.endsWith(".css")) {
                return true;
            }
            return false;
        } else if ("BARCAN-TAG-03".equals(roleTag)) { // Design
            // Design resources: css, icons, mockups, svelte/tsx/jsx (only layout components)
            return (lower.endsWith(".svelte") || lower.endsWith(".css") || lower.endsWith(".png") || lower.endsWith(".svg") || lower.endsWith(".html") || lower.contains("design/")) && !lower.contains("test");
        } else if ("BARCAN-TAG-04".equals(roleTag)) { // AI/ML
            return lower.contains("ai") || lower.contains("ml") || lower.contains("model")
                    || lower.contains("prompt") || lower.endsWith(".py");
        } else if ("BARCAN-TAG-11".equals(roleTag)) { // Frontend
            // React, Svelte, Next.js page layouts
            if (lower.contains("test") || lower.contains("/api/") || lower.endsWith(".prisma") || lower.endsWith(".py")) {
                return false;
            }
            return lower.endsWith(".svelte") || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".jsx") || lower.endsWith(".css") || lower.endsWith(".html");
        } else if ("BARCAN-TAG-05".equals(roleTag)) { // DevOps
            return lower.contains("docker") || lower.contains(".github/workflows") || lower.contains("compose")
                    || lower.contains("build") || lower.endsWith(".yml") || lower.endsWith(".yaml");
        } else if ("BARCAN-TAG-06".equals(roleTag)) { // QA
            return lower.contains("test") || lower.contains("/tests/") || lower.endsWith(".test.ts") || lower.endsWith(".test.js");
        } else if ("BARCAN-TAG-07".equals(roleTag)) { // Security
            return lower.contains("security") || lower.contains("auth") || lower.contains("credential")
                    || lower.contains("permission") || lower.contains("config");
        } else if ("BARCAN-TAG-08".equals(roleTag)) { // Data
            return lower.contains("migration") || lower.contains("schema") || lower.contains("entity")
                    || lower.contains("repository") || lower.endsWith(".sql") || lower.endsWith(".prisma");
        } else if ("BARCAN-TAG-09".equals(roleTag)) { // Delivery management
            return lower.endsWith(".md") || lower.contains("delivery") || lower.contains("plan");
        } else if ("BARCAN-TAG-10".equals(roleTag)) { // Compliance
            return lower.endsWith(".md") || lower.contains("compliance") || lower.contains("legal")
                    || lower.contains("policy");
        }
        return false;
    }

    private String getFeatureName(String wishContent) {
        String lower = wishContent != null ? wishContent.toLowerCase(java.util.Locale.ROOT) : "";
        if (lower.contains("chess") || lower.contains("шахмат")) {
            if (lower.contains("ai") || lower.contains("сложност") || lower.contains("ии")) {
                return "ChessAi";
            }
            return "Chess";
        }
        if (lower.contains("board") || lower.contains("доск")) {
            return "Board";
        }
        // Fallback: try to find any English word of length >= 4
        String[] words = lower.split("[^a-zA-Z0-9]+");
        for (String w : words) {
            if (w.length() >= 4 && !w.equals("with") && !w.equals("from") && !w.equals("that") && !w.equals("this")) {
                return capitalize(w);
            }
        }
        return "NewFeature";
    }

    private String executionNotesForRole(String roleTag) {
        String common = "- Treat the task description, JTBD, Acceptance Criteria, DoD, and file scope as the source of truth.\n"
                + "- Proceed with the smallest safe implementation assumption when details are ambiguous; document assumptions in the PR summary instead of waiting.\n"
                + "- Ask a blocker question only for a concrete contradiction, security risk, or data-loss risk.\n"
                + "- Keep the Jules session short: one atomic result, one branch, one PR, preferably no more than two tightly related source areas.\n"
                + "- Do not expand into new features, broad architecture rewrites, or extra verification work; write remaining slices as follow-up wishlist notes.\n"
                + "- Keep generated local artifacts, reports, screenshots, trace zips, node_modules, and environment files out of the commit.";

        if ("BARCAN-TAG-06".equals(roleTag)) {
            return common + "\n"
                    + "- QA default for business logic verification: use the listed Acceptance Criteria and DoD; continue deepening verification unless a specific contradiction is found.\n"
                    + "- Maintain the intended test pyramid as closely as the codebase allows; prefer unit tests for pure logic, integration tests for API/data flows, and E2E tests for the critical user journeys.\n"
                    + "- Do not commit playwright-report, test-results, trace archives, screenshots, or coverage artifacts.";
        }
        if ("BARCAN-TAG-00".equals(roleTag)) {
            return common + "\n"
                    + "- Integration default: reconcile the implemented pieces into a working branch, remove accidental generated artifacts, and verify build/test commands before opening the PR.";
        }
        return common;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
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

        // Step 1 - Bottleneck Check (TOC)
        if (w.getTocConstraintRef() == null || w.getTocConstraintRef().trim().isEmpty()) {
            errors.add("Step 1 failed: missing bottleneck reference (toc_constraint_ref)");
        }

        // Step 2 - Lean Classification
        if (w.getLeanValue() == null) {
            errors.add("Step 2 failed: lean_value is missing");
        } else if (w.getLeanValue() == LeanValue.waste) {
            errors.add("Step 2 failed: lean_value cannot be 'waste'");
        }

        // Step 3 - JTBD
        if (w.getJtbd() == null || w.getJtbd().trim().isEmpty()) {
            errors.add("Step 3 failed: jtbd is missing");
        }

        // Step 4 - Six Sigma Metric
        if (w.getSixSigmaMetric() == null || w.getSixSigmaMetric().trim().isEmpty()) {
            errors.add("Step 4 failed: six_sigma_metric is missing");
        }

        // Step 5 - Role-Grounded DoD
        String dod = w.getDod();
        if (dod == null || dod.trim().isEmpty()) {
            errors.add("Step 5 failed: DoD is missing");
        } else if (!dod.matches(".*BARCAN-TAG-\\d{2}.*")) {
            errors.add("Step 5 failed: DoD does not reference role refusal criteria (BARCAN-TAG-XX)");
        } else {
            // Step 6 - Design System Reference for UI/design tasks
            if (dod.contains("BARCAN-TAG-03") || dod.contains("BARCAN-TAG-11")) {
                boolean hasDesignSystemRef = dod.contains("docs/DESIGN_SYSTEM.md");
                boolean hasPendingRef = dod.contains("pending: design system not yet defined");

                java.io.File designSystemFile = new java.io.File("docs/DESIGN_SYSTEM.md");
                if (designSystemFile.exists()) {
                    if (!hasDesignSystemRef) {
                        errors.add("Step 6 failed: UI task DoD must reference docs/DESIGN_SYSTEM.md");
                    }
                } else {
                    if (!hasPendingRef) {
                        errors.add("Step 6 failed: design system is missing, so DoD must contain 'pending: design system not yet defined'");
                    }
                }
            }
        }

        // Step 7 - Acceptance Criteria
        if (w.getAcceptanceCriteria() == null || w.getAcceptanceCriteria().trim().isEmpty()) {
            errors.add("Step 7 failed: acceptance_criteria is missing");
        }

        return errors;
    }

    private String buildTaskDescription(WishlistEntity wishlist, String roleTag, String atomicGoal,
                                        String dod, String kano, String cynefin) {
        String jtbd = englishMetadata(wishlist.getJtbd(), fallbackJtbd(wishlist));
        String acceptanceCriteria = englishMetadata(wishlist.getAcceptanceCriteria(), fallbackAcceptanceCriteria(wishlist));

        java.util.Set<String> extractedRoles = extractRoleTags(wishlist);
        if (extractedRoles.isEmpty() && roleTag != null) {
            extractedRoles.add(roleTag);
        }
        String joinedRoles = String.join(", ", extractedRoles);

        StringBuilder sb = new StringBuilder();
        sb.append("Role: ").append(roleTag).append(" - ").append(roleLabel(roleTag)).append("\n");
        sb.append("Source Roles: ").append(joinedRoles).append("\n");
        sb.append("Slice: ").append(sliceTitle(wishlist)).append("\n");
        sb.append("Atomic Goal: ").append(atomicGoal).append("\n");
        sb.append("Kano: ").append(kano).append("\n");
        sb.append("Cynefin: ").append(cynefin).append("\n");
        sb.append("JTBD: ").append(jtbd).append("\n\n");
        if (wishlist.getSource() == com.eneik.production.models.persistence.WishlistSource.role_mismatch_followup) {
            sb.append("Operational Trigger: ")
                    .append(englishMetadata(wishlist.getContent(), "Circuit-breaker recovery for a previous Jules session."))
                    .append("\n\n");
        }
        // Always include the real original wishlist text verbatim, in whatever language it's in - task
        // compilation no longer routes through Gemini, so this is the only place the actual client ask
        // reaches Jules at all. Previously this was omitted for most wishlist sources and the JTBD/
        // acceptance-criteria fields above were the only signal Jules received - when those were
        // AI-fallback placeholders (e.g. during a Gemini outage), Jules had no way to recover the real
        // intent because it never saw the source text.
        String originalBrief = wishlist.getContent();
        if (originalBrief != null && !originalBrief.isBlank()) {
            sb.append("Original Brief (verbatim, may be in any language - read and understand it yourself,\n")
                    .append("do not rely only on the JTBD/Acceptance Criteria above if they look generic):\n")
                    .append(compactLines(originalBrief, 4000))
                    .append("\n\n");
        }
        sb.append("Definition of Done:\n");
        sb.append("- ").append(dod).append("\n");
        sb.append("- One branch and one PR are opened for this role only.\n");
        sb.append("- The PR summary includes the exact verification command and result.\n\n");
        sb.append("Acceptance Criteria:\n").append(compactLines(acceptanceCriteria, 900)).append("\n\n");
        sb.append("Boundaries:\n");
        sb.append("- Write your own clear English PR title and summary describing what you actually built - do not paste the raw Original Brief verbatim into the PR narrative.\n");
        sb.append("- Do not implement adjacent slices or other roles.\n");
        sb.append("- If the session reaches 8 back-and-forth messages, stop with a concrete blocker instead of looping.\n\n");
        sb.append("Execution Notes:\n").append(executionNotesForRole(roleTag));
        return sb.toString();
    }

    private String roleAtomicGoal(WishlistEntity wishlist, String roleTag) {
        boolean recovery = isRecoveryWork(wishlist);
        if (recovery) {
            return switch (roleTag) {
                case "BARCAN-TAG-01" -> "Resolve the smallest architecture or service-boundary blocker from the follow-up wishlist item.";
                case "BARCAN-TAG-03" -> "Resolve the smallest design blocker from the closed Jules session and leave a precise handoff note.";
                case "BARCAN-TAG-02" -> "Resolve the smallest backend/API blocker from the closed Jules session and verify the affected path.";
                case "BARCAN-TAG-04" -> "Resolve the smallest AI/model/context blocker from the follow-up wishlist item and verify the affected path.";
                case "BARCAN-TAG-05" -> "Resolve the smallest build, Docker, CI, or deployment blocker from the follow-up wishlist item.";
                case "BARCAN-TAG-11" -> "Resolve the smallest frontend/browser blocker from the closed Jules session and verify the affected interaction.";
                case "BARCAN-TAG-06" -> "Resolve the smallest verification blocker from the closed Jules session and add or adjust the relevant test only.";
                case "BARCAN-TAG-07" -> "Resolve the smallest security, credential, or access-control blocker from the follow-up wishlist item.";
                case "BARCAN-TAG-08" -> "Resolve the smallest data, schema, migration, or parsing blocker from the follow-up wishlist item.";
                case "BARCAN-TAG-00" -> "Resolve the smallest integration or repository-hygiene blocker from the closed Jules session.";
                case "BARCAN-TAG-09" -> "Clarify the smallest delivery decision needed to unblock the follow-up wishlist item.";
                case "BARCAN-TAG-10" -> "Resolve the smallest compliance, legal wording, or policy blocker from the follow-up wishlist item.";
                default -> "Resolve the smallest role-specific blocker from the closed Jules session without expanding scope.";
            };
        }
        return switch (roleTag) {
            case "BARCAN-TAG-01" -> "Define the smallest architecture or service-boundary decision needed for this JTBD slice.";
            case "BARCAN-TAG-03" -> "Define the smallest UI/design decision needed for this JTBD slice.";
            case "BARCAN-TAG-02" -> "Implement the smallest backend/API/data change needed for this JTBD slice.";
            case "BARCAN-TAG-04" -> "Implement the smallest AI/model/context change needed for this JTBD slice.";
            case "BARCAN-TAG-11" -> "Implement the smallest Svelte/browser interaction needed for this JTBD slice.";
            case "BARCAN-TAG-06" -> "Verify this JTBD slice with the smallest meaningful automated test set.";
            case "BARCAN-TAG-05" -> "Adjust only the build, Docker, or deployment setting needed for this JTBD slice.";
            case "BARCAN-TAG-07" -> "Implement the smallest security, credential, or access-control change needed for this JTBD slice.";
            case "BARCAN-TAG-08" -> "Implement the smallest data, schema, storage, migration, or parsing change needed for this JTBD slice.";
            case "BARCAN-TAG-00" -> "Integrate the completed slice, remove accidental artifacts, and verify the branch is merge-ready.";
            case "BARCAN-TAG-09" -> "Produce the smallest delivery decision, sequencing note, or spike result needed for this JTBD slice.";
            case "BARCAN-TAG-10" -> "Implement the smallest compliance, legal-disclaimer, policy, or regulatory-content change needed for this JTBD slice.";
            default -> "Complete the smallest role-specific implementation step needed for this JTBD slice.";
        };
    }

    private String roleSpecificDod(String roleTag, boolean recovery) {
        if (recovery) {
            return "The role blocker is either fixed and verified, or replaced with one precise follow-up wishlist item. Role: " + roleTag;
        }
        return switch (roleTag) {
            case "BARCAN-TAG-01" -> "Architecture decision, service boundary, or ADR-style note exists and names the next implementation owner. Role: BARCAN-TAG-01";
            case "BARCAN-TAG-03" -> "Design artifact, UI state description, or screenshots exist. Reference docs/DESIGN_SYSTEM.md or state 'pending: design system not yet defined'. Role: BARCAN-TAG-03";
            case "BARCAN-TAG-02" -> "Backend/API/data behavior is implemented and covered by focused unit or integration tests. Role: BARCAN-TAG-02";
            case "BARCAN-TAG-04" -> "AI/model/context behavior is implemented or configured, with deterministic fallback behavior documented and verified. Role: BARCAN-TAG-04";
            case "BARCAN-TAG-11" -> "The browser UI implements the slice, integrates with the API where needed, and follows docs/DESIGN_SYSTEM.md. Role: BARCAN-TAG-11";
            case "BARCAN-TAG-06" -> "Automated tests verify the listed Acceptance Criteria and no generated test artifacts are committed. Role: BARCAN-TAG-06";
            case "BARCAN-TAG-05" -> "Build/deployment configuration is updated and the relevant local verification command passes. Role: BARCAN-TAG-05";
            case "BARCAN-TAG-07" -> "Security, credentials, permissions, or access-control behavior is verified without exposing secrets. Role: BARCAN-TAG-07";
            case "BARCAN-TAG-08" -> "Data model, migration, storage, parsing, or retention behavior is implemented and verified with focused tests. Role: BARCAN-TAG-08";
            case "BARCAN-TAG-00" -> "The slice is integrated across touched components, repository hygiene is clean, and the merge path is verified. Role: BARCAN-TAG-00";
            case "BARCAN-TAG-09" -> "Delivery decision is recorded as a concise handoff note with one concrete next owner role and no implementation scope expansion. Role: BARCAN-TAG-09";
            case "BARCAN-TAG-10" -> "Compliance/legal/policy behavior or content is implemented with clear disclaimer boundaries and verification notes. Role: BARCAN-TAG-10";
            default -> "The role-specific change is complete, verified, and documented in the PR summary. Role: " + roleTag;
        };
    }

    private String normalizeRoleTag(String value) {
        if (value != null && value.matches("BARCAN-TAG-\\d{2}")) {
            return value;
        }
        return "BARCAN-TAG-00";
    }

    private boolean isValidRoleTag(String value) {
        return value != null && value.matches("BARCAN-TAG-(0[0-9]|1[0-1])");
    }

    private String targetRoleForWishlist(WishlistEntity wishlist) {
        if (wishlist.getSourceRoleTag() != null && wishlist.getSourceRoleTag().contains(",")) {
            String firstTag = wishlist.getSourceRoleTag().split(",")[0].trim();
            if (isValidRoleTag(firstTag)) {
                return firstTag;
            }
        }
        if (isValidRoleTag(wishlist.getSourceRoleTag())) {
            return wishlist.getSourceRoleTag();
        }

        String explicitRole = firstNonTechLeadRole(wishlist.getDod());
        if (explicitRole != null) {
            return explicitRole;
        }

        return inferRoleFromWishlist(wishlist);
    }

    private String firstNonTechLeadRole(String value) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("BARCAN-TAG-(0[0-9]|1[0-1])")
                .matcher(value);
        while (matcher.find()) {
            String role = matcher.group();
            if (!TECH_LEAD_ROLE_TAG.equals(role)) {
                return role;
            }
        }
        return null;
    }

    private boolean isRecoveryWork(WishlistEntity wishlist) {
        return wishlist.getSource() == com.eneik.production.models.persistence.WishlistSource.role_mismatch_followup
                || wishlist.getSource() == com.eneik.production.models.persistence.WishlistSource.self_falsification
                || wishlist.getSource() == com.eneik.production.models.persistence.WishlistSource.chaotic_debt;
    }

    private String inferRoleFromWishlist(WishlistEntity wishlist) {
        String source = ((wishlist.getContent() != null ? wishlist.getContent() : "") + " "
                + (wishlist.getJtbd() != null ? wishlist.getJtbd() : "") + " "
                + (wishlist.getAcceptanceCriteria() != null ? wishlist.getAcceptanceCriteria() : "") + " "
                + (wishlist.getDod() != null ? wishlist.getDod() : "")).toLowerCase(java.util.Locale.ROOT);
        if (source.contains("merge") || source.contains("integration") || source.contains("repository hygiene")
                || source.contains("artifact") || source.contains("pr diff")) {
            return "BARCAN-TAG-00";
        }
        if (source.contains("architecture") || source.contains("mvc") || source.contains("microservice")
                || source.contains("service boundary") || source.contains("adr")) {
            return "BARCAN-TAG-01";
        }
        if (source.contains("security") || source.contains("auth") || source.contains("credential")
                || source.contains("permission") || source.contains("access-control") || source.contains("login")) {
            return "BARCAN-TAG-07";
        }
        if (source.contains("database") || source.contains("schema") || source.contains("migration")
                || source.contains("storage") || source.contains("csv") || source.contains("pdf")
                || source.contains("parse") || source.contains("upload")) {
            return "BARCAN-TAG-08";
        }
        if (source.contains("ai") || source.contains("llm") || source.contains("model")
                || source.contains("prompt") || source.contains("rag") || source.contains("embedding")) {
            return "BARCAN-TAG-04";
        }
        if (source.contains("legal") || source.contains("tax law") || source.contains("compliance")
                || source.contains("regulatory") || source.contains("disclaimer")) {
            return "BARCAN-TAG-10";
        }
        if (source.contains("test") || source.contains("qa") || source.contains("verify")
                || source.contains("verification") || source.contains("e2e")) {
            return "BARCAN-TAG-06";
        }
        if (source.contains("docker") || source.contains("deploy") || source.contains("ci")
                || source.contains("build") || source.contains("pipeline")) {
            return "BARCAN-TAG-05";
        }
        if (source.contains("design") || source.contains("mockup") || source.contains("wireframe")
                || source.contains("ux")) {
            return "BARCAN-TAG-03";
        }
        if (source.contains("frontend") || source.contains("svelte") || source.contains("browser")
                || source.contains("screen") || source.contains("page") || source.contains("button")
                || source.contains("form") || source.contains("ui")) {
            return "BARCAN-TAG-11";
        }
        return "BARCAN-TAG-02";
    }

    private String roleLabel(String roleTag) {
        return switch (roleTag) {
            case "BARCAN-TAG-00" -> "Integration Guardian";
            case "BARCAN-TAG-01" -> "Architecture";
            case "BARCAN-TAG-02" -> "Backend API";
            case "BARCAN-TAG-03" -> "Product Design";
            case "BARCAN-TAG-04" -> "AI/ML";
            case "BARCAN-TAG-05" -> "DevOps";
            case "BARCAN-TAG-06" -> "QA Verification";
            case "BARCAN-TAG-07" -> "Security";
            case "BARCAN-TAG-08" -> "Data";
            case "BARCAN-TAG-09" -> "Delivery Management";
            case "BARCAN-TAG-10" -> "Compliance";
            case "BARCAN-TAG-11" -> "Frontend UI";
            default -> "Role Worker";
        };
    }

    private String kanoClass(WishlistEntity wishlist, java.util.Set<String> extractedRoles) {
        if (extractedRoles.size() > 1) {
            return "Must-Be";
        }
        String source = ((wishlist.getContent() != null ? wishlist.getContent() : "") + " "
                + (wishlist.getJtbd() != null ? wishlist.getJtbd() : "") + " "
                + (wishlist.getDod() != null ? wishlist.getDod() : "")).toLowerCase(java.util.Locale.ROOT);
        if (source.contains("kano: attractive") || source.contains("delight") || source.contains("wow")) {
            return "Attractive";
        }
        if (source.contains("kano: performance") || source.contains("faster") || source.contains("optimize")) {
            return "Performance";
        }
        if (wishlist.getLeanValue() == LeanValue.valuable) {
            return "Performance";
        }
        if (wishlist.getLeanValue() == LeanValue.waste) {
            return "Reverse/Waste";
        }
        return "Must-Be";
    }

    private String cynefinDomain(WishlistEntity wishlist) {
        String source = ((wishlist.getContent() != null ? wishlist.getContent() : "") + " "
                + (wishlist.getJtbd() != null ? wishlist.getJtbd() : "") + " "
                + (wishlist.getDod() != null ? wishlist.getDod() : "")).toLowerCase(java.util.Locale.ROOT);
        if (wishlist.getSource() == com.eneik.production.models.persistence.WishlistSource.self_falsification
                || source.contains("cynefin: chaotic")) {
            return "chaotic";
        }
        if (source.contains("cynefin: complex") || source.contains("spike") || source.contains("research") || source.contains("unknown")) {
            return "complex";
        }
        if (wishlist.getSource() == com.eneik.production.models.persistence.WishlistSource.role_mismatch_followup
                || source.contains("cynefin: complicated") || source.contains("integration") || source.contains("migration")) {
            return "complicated";
        }
        return "clear";
    }

    private String sliceTitle(WishlistEntity wishlist) {
        String content = wishlist.getContent();
        if (content != null && (content.startsWith("Internal slice ")
                || content.startsWith("Internal UI slice ")
                || content.startsWith("Internal work item ")
                || content.startsWith("Internal UI work item "))) {
            return compactLines(englishMetadata(content, "Compiled JTBD slice " + shortId(wishlist.getId())), 140);
        }
        return "Compiled JTBD slice " + shortId(wishlist.getId());
    }

    private String fileScopeSource(WishlistEntity wishlist) {
        return ((wishlist.getContent() != null ? wishlist.getContent() : "") + " "
                + (wishlist.getJtbd() != null ? wishlist.getJtbd() : "") + " "
                + (wishlist.getAcceptanceCriteria() != null ? wishlist.getAcceptanceCriteria() : "")).trim();
    }

    private String englishMetadata(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String compact = compactLines(value, 1_000);
        String lower = compact.toLowerCase(java.util.Locale.ROOT);
        if (containsNonEnglishSignal(compact)
                || "automate and transform".equals(lower)
                || "given task merged, then verify feature".equals(lower)) {
            return fallback;
        }
        return compact;
    }

    private String fallbackJtbd(WishlistEntity wishlist) {
        return "When this slice is delivered, the user can complete one small verifiable capability safely, so project progress is measurable without a long Jules session.";
    }

    private String fallbackAcceptanceCriteria(WishlistEntity wishlist) {
        return "Given this role completes the Atomic Goal, When the relevant verification command runs, Then the change passes without unrelated scope.\n"
                + "Given the change is reviewed, When the PR diff is inspected, Then it contains only source, config, test, or documentation files needed for this slice.\n"
                + "Given a blocker remains after one objective attempt, When the Jules session would otherwise loop, Then the agent stops and records one concrete blocker or follow-up.";
    }

    private String compactLines(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
        compact = compact.replaceAll("\\n{3,}", "\n\n");
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String shortId(UUID id) {
        if (id == null) {
            return "unknown";
        }
        return id.toString().substring(0, 8);
    }

    private String getRoleSpecificAssignment(WishlistEntity wishlist, String roleTag) {
        String content = wishlist.getContent();
        String jtbd = wishlist.getJtbd();
        String taskSubject = (jtbd != null && !jtbd.isBlank() && !jtbd.toLowerCase(java.util.Locale.ROOT).contains("automate and transform")) ? jtbd : content;
        taskSubject = englishTaskSubject(wishlist, taskSubject);

        // Truncate to a single concise sentence or 120 chars if taskSubject is still too long/raw
        if (taskSubject != null && taskSubject.length() > 120) {
            int dotIndex = taskSubject.indexOf('.');
            if (dotIndex > 20 && dotIndex < 120) {
                taskSubject = taskSubject.substring(0, dotIndex + 1);
            } else {
                taskSubject = taskSubject.substring(0, 117) + "...";
            }
        }

        boolean isChess = content != null && (content.toLowerCase(java.util.Locale.ROOT).contains("шахмат") ||
                          content.toLowerCase(java.util.Locale.ROOT).contains("chess"));

        if (isChess) {
            switch (roleTag) {
                case "BARCAN-TAG-03":
                    return "Design the 3D chessboard scene, including piece materials, camera parameters, and lighting in one coherent visual style.";
                case "BARCAN-TAG-02":
                    return "Implement chess rules and a computer-opponent algorithm with three difficulty levels using search depth or an evaluation function.";
                case "BARCAN-TAG-11":
                    return "Connect the 3D visualization to game logic: piece selection, legal-move highlighting, and move submission to the engine.";
                case "BARCAN-TAG-06":
                    return "Create an automated E2E test for the end-to-end game flow against the computer opponent.";
            }
        }

        switch (roleTag) {
            case "BARCAN-TAG-03":
                return "Design the user interface, screen states, and visual interaction elements for: \"" + taskSubject + "\" using docs/DESIGN_SYSTEM.md.";
            case "BARCAN-TAG-02":
                return "Implement backend business logic, API endpoints, database migrations, and unit tests for: \"" + taskSubject + "\".";
            case "BARCAN-TAG-11":
                return "Implement Svelte frontend components, browser interactions, and API integration for: \"" + taskSubject + "\" using docs/DESIGN_SYSTEM.md.";
            case "BARCAN-TAG-06":
                return "Write automated E2E and integration tests to verify: \"" + taskSubject + "\".";
            case "BARCAN-TAG-05":
                return "Configure the CI/CD pipeline, Dockerfile, build settings, and deployment environment for: \"" + taskSubject + "\".";
            default:
                return "Implement the technical requirements for role " + roleTag + " for: \"" + taskSubject + "\".";
        }
    }

    private String englishTaskSubject(WishlistEntity wishlist, String rawSubject) {
        if (rawSubject == null || rawSubject.isBlank()) {
            return "the requested product capability from wishlist item " + wishlist.getId();
        }
        String compact = rawSubject.replaceAll("\\s+", " ").trim();
        if (containsNonEnglishSignal(compact)) {
            return "the client-requested capability from wishlist item " + wishlist.getId()
                    + "; use the English JTBD and Acceptance Criteria as the source of truth";
        }
        return compact;
    }

    private boolean containsNonEnglishSignal(String value) {
        if (value == null) {
            return false;
        }
        return value.matches(".*[\\p{IsCyrillic}].*")
                || value.contains("Ð")
                || value.contains("Ñ")
                || value.contains("Р")
                || value.contains("С");
    }
}
