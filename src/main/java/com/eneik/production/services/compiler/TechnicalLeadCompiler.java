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
    private final ProjectHotspotFileRepository projectHotspotFileRepository;
    private final GateOrchestrator gateOrchestrator;
    private final BottleneckAwarePriorityService bottleneckAwarePriorityService;
    private final ObjectMapper objectMapper;

    private static final String TECH_LEAD_ROLE_TAG = "BARCAN-TAG-09";

    public TechnicalLeadCompiler(WishlistRepository wishlistRepository,
                                 TaskRepository taskRepository,
                                 ProjectRepository projectRepository,
                                 RoleRepository roleRepository,
                                 ProjectGenerationStateRepository projectGenerationStateRepository,
                                 ProjectHotspotFileRepository projectHotspotFileRepository,
                                 GateOrchestrator gateOrchestrator,
                                 BottleneckAwarePriorityService bottleneckAwarePriorityService,
                                 ObjectMapper objectMapper) {
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.roleRepository = roleRepository;
        this.projectGenerationStateRepository = projectGenerationStateRepository;
        this.projectHotspotFileRepository = projectHotspotFileRepository;
        this.gateOrchestrator = gateOrchestrator;
        this.bottleneckAwarePriorityService = bottleneckAwarePriorityService;
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
            // Task 1: TAG-03 Design (Parallel)
            TaskEntity designTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-03",
                "Спроектировать 3D-сцену шахматной доски, включая материалы фигур, параметры камеры и освещения в едином визуальном стиле.",
                "Скриншот или ссылка на макет сцены с доской и фигурами в двух разрешениях (десктоп/мобайл). Reference: docs/DESIGN_SYSTEM.md",
                createdTasks, null, false);

            // Task 2: TAG-02 Backend (Parallel)
            createAndSaveTask(project, wishlist, "BARCAN-TAG-02", 
                "Реализовать логику шахматных правил и алгоритм ИИ с 3 уровнями сложности (через глубину поиска или оценочную функцию).",
                "Юнит-тесты, показывающие разное качество и глубину хода ИИ в одной и той же позиции на 3 уровнях сложности. Роль: BARCAN-TAG-02",
                createdTasks, null, false);

            // Task 3: TAG-11 Frontend (Depends on Design)
            TaskEntity frontendTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-11",
                "Подключить 3D-визуализацию к логике игры: обработка кликов по фигурам, подсветка доступных ходов, отправка хода в движок.",
                "Интерактивное взаимодействие работает в интерфейсе, невалидные ходы блокируются визуально. Зависит от: TAG-03, TAG-02. Reference: docs/DESIGN_SYSTEM.md",
                createdTasks, designTask.getId(), false);

            // Task 4: TAG-00 Integration
            TaskEntity integrationTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-00",
                wishlist.getContent() + " - Integration",
                "Все модули интегрированы, hotspot-файлы обновлены, конфликтов нет.",
                createdTasks, frontendTask.getId(), true);

            // Task 5: TAG-06 QA (Depends on Integration)
            createAndSaveTask(project, wishlist, "BARCAN-TAG-06", 
                "Разработать автоматизированный E2E тест на сквозной игровой процесс против компьютера.",
                "Автотест успешно проходит полный цикл (Старт -> Ходы -> Окончание игры) на всех трёх уровнях сложности. Зависит от: TAG-11. Роль: BARCAN-TAG-06",
                createdTasks, integrationTask.getId(), false);
        } else if (uiExists) {
            // Task 1: TAG-03 Design (Parallel)
            TaskEntity designTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-03",
                wishlist.getContent(), 
                "Figma-макет или скриншоты интерфейса функции. Ссылается на docs/DESIGN_SYSTEM.md или содержит 'pending: design system not yet defined'",
                createdTasks, null, false);

            // Task 2: TAG-02 Backend (Parallel)
            createAndSaveTask(project, wishlist, "BARCAN-TAG-02", 
                wishlist.getContent() + " - Backend logic and API",
                "Unit-тесты, покрывающие бизнес-сценарии и логику обработки данных для этой роли. Роль: BARCAN-TAG-02",
                createdTasks, null, false);

            // Task 3: TAG-11 Frontend (Depends on Design)
            TaskEntity frontendTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-11",
                wishlist.getContent() + " - Frontend integration",
                "Интерактивный UI в браузере корректно взаимодействует с бэкенд API. Роль: BARCAN-TAG-11. Reference: docs/DESIGN_SYSTEM.md",
                createdTasks, designTask.getId(), false);

            // Task 4: TAG-00 Integration
            TaskEntity integrationTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-00",
                wishlist.getContent() + " - Integration",
                "Все модули интегрированы, hotspot-файлы обновлены, конфликтов нет.",
                createdTasks, frontendTask.getId(), true);

            // Task 5: TAG-06 QA (Depends on Integration)
            createAndSaveTask(project, wishlist, "BARCAN-TAG-06", 
                wishlist.getContent() + " - QA E2E Testing",
                "Автотесты успешно проходят для всех основных бизнес-сценариев. Роль: BARCAN-TAG-06",
                createdTasks, integrationTask.getId(), false);
        } else {
            // Task 1: TAG-02 Backend (Parallel)
            TaskEntity backendTask = createAndSaveTask(project, wishlist, "BARCAN-TAG-02",
                wishlist.getContent(),
                "Unit-тесты, покрывающие бизнес-сценарии и логику обработки данных для этой роли. Роль: BARCAN-TAG-02",
                createdTasks, null, false);

            // Task 2: TAG-06 QA (Depends on Backend)
            createAndSaveTask(project, wishlist, "BARCAN-TAG-06", 
                wishlist.getContent() + " - QA E2E Testing",
                "Автотесты успешно проходят для всех основных бизнес-сценариев. Роль: BARCAN-TAG-06",
                createdTasks, backendTask.getId(), false);
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
                                   String description, String dod, java.util.List<TaskEntity> createdTasks, UUID dependsOn, boolean isIntegrationTask) {
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
        task.setDependsOn(dependsOn);
        task.setPriority(bottleneckAwarePriorityService.computePriority(wishlist.getTocConstraintRef()));
        task.setFileScope(determineFileScope(project, roleTag, wishlist.getContent(), isIntegrationTask));
        
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
        task.setFileScope(determineFileScope(roleTag, wishlist.getContent()));
        
        TaskEntity saved = taskRepository.save(task);
        createdTasks.add(saved);
        return saved;
    }

    private String determineFileScope(ProjectEntity project, String roleTag, String wishContent, boolean isIntegrationTask) {
        String lowerWish = (wishContent != null ? wishContent : "").toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> paths = new java.util.ArrayList<>();
        
        // Extract keywords for search
        java.util.List<String> keywords = new java.util.ArrayList<>();
        String[] words = lowerWish.split("\\W+");
        for (String w : words) {
            if (w.length() > 3 && !w.equals("this") && !w.equals("that")) {
                keywords.add(w);
            }
        }
        if (keywords.isEmpty()) keywords.add("main");

        // Determine target extension
        final String finalExtension;
        String defaultNewPath = "src/main/java/com/eneik/production/services/NewService.java";
        if ("BARCAN-TAG-03".equals(roleTag) || "BARCAN-TAG-11".equals(roleTag)) {
            finalExtension = ".svelte";
            defaultNewPath = "frontend/src/components/NewComponent.svelte";
        } else if ("BARCAN-TAG-06".equals(roleTag)) {
            finalExtension = "Test.java";
            defaultNewPath = "src/test/java/com/eneik/production/services/NewServiceTest.java";
        } else {
            finalExtension = ".java";
        }

        boolean found = false;
        if (project.getWorkspacePath() != null) {
            java.io.File workspace = new java.io.File(project.getWorkspacePath());
            if (workspace.exists() && workspace.isDirectory()) {
                try (java.util.stream.Stream<java.nio.file.Path> walkStream = java.nio.file.Files.walk(workspace.toPath())) {
                    java.util.List<java.nio.file.Path> matchedPaths = walkStream
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(finalExtension))
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                            return keywords.stream().anyMatch(name::contains);
                        })
                        .limit(3)
                        .toList();

                    for (java.nio.file.Path p : matchedPaths) {
                        paths.add(workspace.toPath().relativize(p).toString().replace("\\", "/"));
                        found = true;
                    }
                } catch (Exception e) {
                    // Ignore and fallback
                }
            }
        }

        if (!found) {
            if (lowerWish.contains("chess") || lowerWish.contains("шахмат")) {
                if ("BARCAN-TAG-03".equals(roleTag) || "BARCAN-TAG-11".equals(roleTag)) {
                    paths.add("frontend/src/components/ChessBoard.svelte");
                } else if ("BARCAN-TAG-02".equals(roleTag)) {
                    paths.add("src/main/java/com/eneik/production/services/ChessService.java");
                } else if ("BARCAN-TAG-06".equals(roleTag)) {
                    paths.add("src/test/java/com/eneik/production/services/ChessServiceTest.java");
                } else {
                    paths.add(defaultNewPath);
                }
            } else if (lowerWish.contains("board") || lowerWish.contains("доск")) {
                if ("BARCAN-TAG-03".equals(roleTag) || "BARCAN-TAG-11".equals(roleTag)) {
                    paths.add("frontend/src/components/Board.svelte");
                } else if ("BARCAN-TAG-02".equals(roleTag)) {
                    paths.add("src/main/java/com/eneik/production/services/BoardService.java");
                } else {
                    paths.add(defaultNewPath);
                }
            } else {
                paths.add(defaultNewPath);
            }
        }

        if (isIntegrationTask && project.getId() != null) {
            java.util.List<ProjectHotspotFileEntity> hotspots = projectHotspotFileRepository.findByProjectId(project.getId());
            for (ProjectHotspotFileEntity hotspot : hotspots) {
                if (!paths.contains(hotspot.getFilePath())) {
                    paths.add(hotspot.getFilePath());
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(paths);
        } catch (Exception e) {
            return "[]";
        }
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
