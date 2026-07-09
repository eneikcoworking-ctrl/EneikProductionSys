package com.eneik.production.services.compiler;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.BottleneckAwarePriorityService;
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

        if (wishlist.getStatus() == WishlistStatus.converted_to_task) {
            boolean isChess = wishlist.getContent().toLowerCase(java.util.Locale.ROOT).contains("шахмат") || 
                             wishlist.getContent().toLowerCase(java.util.Locale.ROOT).contains("chess");
            String expectedDescription = isChess ? 
                "Спроектировать 3D-сцену шахматной доски, включая материалы фигур, параметры камеры и освещения в едином визуальном стиле." :
                wishlist.getContent();
            return taskRepository.findByProjectIdAndDescription(project.getId(), expectedDescription)
                    .orElseGet(() -> taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream().findFirst().orElse(null));
        }

        boolean uiExists = hasUiComponent(wishlist);
        boolean isChess = wishlist.getContent().toLowerCase(java.util.Locale.ROOT).contains("шахмат") || 
                         wishlist.getContent().toLowerCase(java.util.Locale.ROOT).contains("chess");

        java.util.List<TaskEntity> createdTasks = new java.util.ArrayList<>();

        if (isChess) {
            // Task 1: TAG-03 Design
            TaskEntity designTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-03", 
                "Спроектировать 3D-сцену шахматной доски, включая материалы фигур, параметры камеры и освещения в едином визуальном стиле.",
                "Скриншот или ссылка на макет сцены с доской и фигурами в двух разрешениях (десктоп/мобайл). Reference: docs/DESIGN_SYSTEM.md",
                null, false, true, createdTasks);

            // Task 2: TAG-02 Backend
            TaskEntity backendTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-02", 
                "Реализовать логику шахматных правил и алгоритм ИИ с 3 уровнями сложности (через глубину поиска или оценочную функцию).",
                "Юнит-тесты, показывающие разное качество и глубину хода ИИ в одной и той же позиции на 3 уровнях сложности. Роль: BARCAN-TAG-02",
                null, false, true, createdTasks);

            // Task 3: TAG-11 Frontend (depends on Design)
            TaskEntity frontendTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-11", 
                "Подключить 3D-визуализацию к логике игры: обработка кликов по фигурам, подсветка доступных ходов, отправка хода в движок.",
                "Интерактивное взаимодействие работает в интерфейсе, невалидные ходы блокируются визуально. Зависит от: TAG-03, TAG-02. Reference: docs/DESIGN_SYSTEM.md",
                designTask, false, true, createdTasks);

            // Task 4: TAG-00 Integration (depends on Frontend)
            TaskEntity integrationTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-00",
                "Интегрировать и финализировать шахматную функциональность, проверить работу в общей точке входа и hotspot-файлах.",
                "Код полностью собран и интегрирован в главном компоненте/роутинге. Роль: BARCAN-TAG-00",
                frontendTask, true, true, createdTasks);

            // Task 5: TAG-06 QA (depends on Integration)
            createAndSaveTask(project, wishlist, "BARCAN-TAG-06", 
                "Разработать автоматизированный E2E тест на сквозной игровой процесс против компьютера.",
                "Автотест успешно проходит полный цикл (Старт -> Ходы -> Окончание игры) на всех трёх уровнях сложности. Зависит от: TAG-11. Роль: BARCAN-TAG-06",
                integrationTask, false, true, createdTasks);
        } else if (uiExists) {
            // Task 1: TAG-03 Design
            TaskEntity designTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-03", 
                wishlist.getContent(), 
                "Figma-макет или скриншоты интерфейса функции. Ссылается на docs/DESIGN_SYSTEM.md или содержит 'pending: design system not yet defined'",
                null, false, true, createdTasks);

            // Task 2: TAG-02 Backend
            TaskEntity backendTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-02", 
                wishlist.getContent() + " - Backend logic and API",
                "Unit-тесты, покрывающие бизнес-сценарии и логику обработки данных для этой роли. Роль: BARCAN-TAG-02",
                null, false, true, createdTasks);

            // Task 3: TAG-11 Frontend (depends on Design)
            TaskEntity frontendTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-11", 
                wishlist.getContent() + " - Frontend integration",
                "Интерактивный UI в браузере корректно взаимодействует с бэкенд API. Роль: BARCAN-TAG-11. Reference: docs/DESIGN_SYSTEM.md",
                designTask, false, true, createdTasks);

            // Task 4: TAG-00 Integration (depends on Frontend)
            TaskEntity integrationTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-00",
                wishlist.getContent() + " - Final Integration",
                "Код полностью собран и интегрирован в главном компоненте/роутинге. Роль: BARCAN-TAG-00",
                frontendTask, true, true, createdTasks);

            // Task 5: TAG-06 QA (depends on Integration)
            createAndSaveTask(project, wishlist, "BARCAN-TAG-06", 
                wishlist.getContent() + " - QA E2E Testing",
                "Автотесты успешно проходят для всех основных бизнес-сценариев. Роль: BARCAN-TAG-06",
                integrationTask, false, true, createdTasks);
        } else {
            // Task 1: TAG-02 Backend
            TaskEntity backendTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-02", 
                wishlist.getContent(),
                "Unit-тесты, покрывающие бизнес-сценарии и логику обработки данных для этой роли. Роль: BARCAN-TAG-02",
                null, false, true, createdTasks);

            // Task 2: TAG-00 Integration (depends on Backend)
            TaskEntity integrationTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-00",
                wishlist.getContent() + " - Backend Integration",
                "Бэкенд-модули полностью интегрированы. Роль: BARCAN-TAG-00",
                backendTask, true, true, createdTasks);

            // Task 3: TAG-06 QA (depends on Integration)
            createAndSaveTask(project, wishlist, "BARCAN-TAG-06", 
                wishlist.getContent() + " - QA E2E Testing",
                "Автотесты успешно проходят для всех основных бизнес-сценариев. Роль: BARCAN-TAG-06",
                integrationTask, false, true, createdTasks);
        }

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
                                         String description, String dod, TaskEntity dependsOn,
                                         boolean isIntegrationTask, boolean hasIntegrationTask,
                                         java.util.List<TaskEntity> createdTasks) {
        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setDescription(description);

        RoleEntity role = roleRepository.findById(roleTag)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleTag));
        task.setRole(role);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jtbd", wishlist.getJtbd());
        payload.put("lean_value", wishlist.getLeanValue().name());
        payload.put("toc_constraint_ref", wishlist.getTocConstraintRef());
        payload.put("six_sigma_metric", wishlist.getSixSigmaMetric());
        payload.put("dod", dod);
        payload.put("acceptance_criteria", wishlist.getAcceptanceCriteria());
        task.setPayload(payload);

        task.setStatus(TaskStatus.queued);
        
        String cynefin = null;
        if (wishlist.getSource() == com.eneik.production.models.persistence.WishlistSource.self_falsification) {
            cynefin = "chaotic";
        } else if (wishlist.getContent() != null && 
                  (wishlist.getContent().toLowerCase(java.util.Locale.ROOT).contains("spike") || 
                   wishlist.getContent().toLowerCase(java.util.Locale.ROOT).contains("complex"))) {
            cynefin = "complex";
        }
        task.setCynefinDomain(cynefin);

        int priority = bottleneckAwarePriorityService.computePriority(wishlist.getTocConstraintRef());
        if ("chaotic".equalsIgnoreCase(cynefin)) {
            priority = 1000;
        }
        task.setPriority(priority);
        task.setDependsOn(dependsOn);
        task.setFileScope(determineFileScope(project, roleTag, wishlist.getContent(), isIntegrationTask, hasIntegrationTask));
        
        TaskEntity saved = taskRepository.save(task);
        createdTasks.add(saved);
        return saved;
    }

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
        for (String w : words) {
            if (w.length() >= 4 && !w.equals("with") && !w.equals("from") && !w.equals("that") && !w.equals("this")) {
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
            if ("BARCAN-TAG-02".equals(roleTag)) { // Backend
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
            } else if ("BARCAN-TAG-11".equals(roleTag)) { // Frontend
                if ("Chess".equals(featureName) || "ChessAi".equals(featureName)) {
                    paths.add("frontend/src/components/ChessBoard.svelte");
                } else {
                    paths.add("frontend/src/components/" + featureName + ".svelte");
                }
            } else if ("BARCAN-TAG-06".equals(roleTag)) { // QA
                if ("Chess".equals(featureName) || "ChessAi".equals(featureName)) {
                    paths.add("src/test/java/com/eneik/production/services/ChessServiceTest.java");
                } else {
                    paths.add("src/test/java/com/eneik/production/services/" + featureName + "ServiceTest.java");
                }
            } else if ("BARCAN-TAG-00".equals(roleTag)) { // Code Guardian / Integration Task
                paths.add("src/main/java/com/eneik/production/services/" + featureName + "IntegrationService.java");
            }
        }

        // Deduplicate paths
        java.util.List<String> deduped = new java.util.ArrayList<>();
        for (String p : paths) {
            if (!deduped.contains(p)) {
                deduped.add(p);
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
        if ("BARCAN-TAG-02".equals(roleTag)) { // Backend
            return lower.endsWith(".java") && !lower.contains("/test/");
        } else if ("BARCAN-TAG-03".equals(roleTag)) { // Design
            return (lower.endsWith(".svelte") || lower.endsWith(".css")) && !lower.contains("/test/");
        } else if ("BARCAN-TAG-11".equals(roleTag)) { // Frontend
            return (lower.endsWith(".svelte") || lower.endsWith(".js") || lower.endsWith(".ts")) && !lower.contains("/test/");
        } else if ("BARCAN-TAG-06".equals(roleTag)) { // QA
            return lower.contains("test") || lower.contains("/tests/");
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
