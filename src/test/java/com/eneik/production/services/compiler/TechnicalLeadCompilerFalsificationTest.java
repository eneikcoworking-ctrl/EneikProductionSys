package com.eneik.production.services.compiler;

import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectHotspotFileEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.ProjectFileClaimRepository;
import com.eneik.production.repositories.ProjectGenerationStateRepository;
import com.eneik.production.repositories.ProjectHotspotFileRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.BottleneckAwarePriorityService;
import com.eneik.production.services.FeatureService;
import com.eneik.production.services.GeminiContextService;
import com.eneik.production.services.gate.GateOrchestrator;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Falsification test suite for {@link TechnicalLeadCompiler} grounding Stage 4 invariants:
 *
 * <p>1. Searle (D007, {@code DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER}):
 * Statuses such as {@code NamespaceAuditStatus} (ADMISSIBLE, VIOLATION, UNKNOWN_NAMESPACE) are institutional
 * facts backed by deterministic rules ("X counts as Y in context C") with no silent pass to admissible.
 * Violations are explicitly registered in the defect journal audit ledger.
 *
 * <p>2. Varzi (D002, {@code AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER}):
 * Actual domain objects possess identity, lineage, and lifecycle rooted in an aggregate: tasks cannot be
 * orphaned or created ad-hoc; every task must inherit an epic identity lazily minted or resolved via
 * {@link FeatureService#resolveOrCreateFeatureId}.
 *
 * <p>3. Raz (D006, {@code DZHOZEF_RAZ_01_PROHIBITION_AS_CODE}):
 * Prohibitions are exclusionary reasons implemented as executable denial paths with explainable reasons.
 * When predicted scope violates product namespace or is unknown, task dispatch is halted, task status
 * is set to {@code TaskStatus.blocked}, dispatch status records the refusal, and file scope is forced to empty {@code []}.
 */
class TechnicalLeadCompilerFalsificationTest {

    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private ProjectRepository projectRepository;
    private RoleRepository roleRepository;
    private ProjectGenerationStateRepository projectGenerationStateRepository;
    private GateOrchestrator gateOrchestrator;
    private BottleneckAwarePriorityService bottleneckAwarePriorityService;
    private ObjectMapper objectMapper;
    private ProjectHotspotFileRepository projectHotspotFileRepository;
    private FeatureService featureService;
    private GitHubPullRequestService gitHubPullRequestService;
    private ProjectFileClaimRepository projectFileClaimRepository;
    private GeminiContextService geminiContextService;
    private DefectJournalService defectJournalService;

    private TechnicalLeadCompiler compiler;

    @BeforeEach
    void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        projectRepository = mock(ProjectRepository.class);
        roleRepository = mock(RoleRepository.class);
        projectGenerationStateRepository = mock(ProjectGenerationStateRepository.class);
        gateOrchestrator = mock(GateOrchestrator.class);
        bottleneckAwarePriorityService = mock(BottleneckAwarePriorityService.class);
        objectMapper = new ObjectMapper();
        projectHotspotFileRepository = mock(ProjectHotspotFileRepository.class);
        featureService = mock(FeatureService.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        projectFileClaimRepository = mock(ProjectFileClaimRepository.class);
        geminiContextService = mock(GeminiContextService.class);
        defectJournalService = mock(DefectJournalService.class);

        // Standard save answer: ensure task ID is populated if absent
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(UUID.randomUUID());
            }
            return task;
        });

        when(roleRepository.findById(anyString())).thenAnswer(invocation -> {
            String tag = invocation.getArgument(0);
            RoleEntity role = new RoleEntity();
            role.setTag(tag);
            role.setDescription("Role " + tag);
            return Optional.of(role);
        });

        compiler = new TechnicalLeadCompiler(
                wishlistRepository,
                taskRepository,
                projectRepository,
                roleRepository,
                projectGenerationStateRepository,
                gateOrchestrator,
                bottleneckAwarePriorityService,
                objectMapper,
                projectHotspotFileRepository,
                featureService,
                gitHubPullRequestService,
                projectFileClaimRepository,
                geminiContextService,
                defectJournalService
        );
    }

    private WishlistEntity createValidWishlist(UUID projectId, String roleTag) {
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setProjectId(projectId);
        wishlist.setCompiledByRole(roleTag != null ? roleTag : "BARCAN-TAG-02");
        wishlist.setSourceRoleTag(roleTag != null ? roleTag : "BARCAN-TAG-02");
        wishlist.setTocConstraintRef("BOTTLENECK-01");
        wishlist.setLeanValue(LeanValue.valuable);
        wishlist.setJtbd("Ensure reliable player matchmaking");
        wishlist.setSixSigmaMetric("DPMO < 3.4");
        wishlist.setDod("DoD satisfies BARCAN-TAG-02 backend criteria");
        wishlist.setAcceptanceCriteria("Given valid credentials, when authenticating, then return JWT token.");
        wishlist.setContent("Implement player matchmaking service and chess lobby logic.");
        return wishlist;
    }

    private ProjectEntity createValidProject(String productNamespace) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);
        project.setSlug("test-fiftieth");
        project.setRepositoryName("eneikcoworking-ctrl/test-fiftieth");
        project.setProductNamespace(productNamespace);
        return project;
    }

    // =========================================================================
    // 1. Searle (D007): DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER
    // =========================================================================

    @Test
    @DisplayName("Searle [D007]: auditPathsAgainstNamespace generates institutional facts with strict 3-state outcomes (no silent pass)")
    void falsifySearle_institutionalFactRegister_threeExplicitOutcomes() {
        String productNamespace = "com.eneik.epidemiology";
        String factoryDir = TechnicalLeadCompiler.FACTORY_PACKAGE_ROOT.replace('.', '/');

        // Case A: Admissible product path
        TechnicalLeadCompiler.NamespaceAuditResult admissibleResult = TechnicalLeadCompiler.auditPathsAgainstNamespace(
                productNamespace, true, false,
                List.of("src/main/java/com/eneik/epidemiology/services/MatchService.java")
        );
        assertThat(admissibleResult.status())
                .as("Paths strictly within product namespace must count as ADMISSIBLE")
                .isEqualTo(TechnicalLeadCompiler.NamespaceAuditStatus.ADMISSIBLE);
        assertThat(admissibleResult.offendingPaths()).isEmpty();

        // Case B: Factory root violation
        String factoryOffending = "src/main/java/" + factoryDir + "/compiler/TechnicalLeadCompiler.java";
        TechnicalLeadCompiler.NamespaceAuditResult factoryViolationResult = TechnicalLeadCompiler.auditPathsAgainstNamespace(
                productNamespace, true, false,
                List.of(factoryOffending)
        );
        assertThat(factoryViolationResult.status())
                .as("Paths inside factory package root must count as VIOLATION")
                .isEqualTo(TechnicalLeadCompiler.NamespaceAuditStatus.VIOLATION);
        assertThat(factoryViolationResult.offendingPaths()).containsExactly(factoryOffending);

        // Case C: Foreign stack violation (Next.js/Prisma in Java stack)
        String prismaOffending = "prisma/schema.prisma";
        TechnicalLeadCompiler.NamespaceAuditResult foreignStackResult = TechnicalLeadCompiler.auditPathsAgainstNamespace(
                productNamespace, true, false,
                List.of(prismaOffending)
        );
        assertThat(foreignStackResult.status())
                .as("Next.js/Prisma artifacts on Java project must count as VIOLATION")
                .isEqualTo(TechnicalLeadCompiler.NamespaceAuditStatus.VIOLATION);
        assertThat(foreignStackResult.offendingPaths()).containsExactly(prismaOffending);

        // Case D: Unconfigured namespace with Java paths -> UNKNOWN_NAMESPACE (No Silent Pass!)
        String unverifiedJava = "src/main/java/org/example/CustomService.java";
        TechnicalLeadCompiler.NamespaceAuditResult unknownResult = TechnicalLeadCompiler.auditPathsAgainstNamespace(
                null, true, false,
                List.of(unverifiedJava)
        );
        assertThat(unknownResult.status())
                .as("Unconfigured product namespace with Java paths must produce UNKNOWN_NAMESPACE, never passing silently")
                .isEqualTo(TechnicalLeadCompiler.NamespaceAuditStatus.UNKNOWN_NAMESPACE);
        assertThat(unknownResult.offendingPaths()).containsExactly(unverifiedJava);

        // Case E: Unconfigured namespace with stack-neutral documentation paths -> ADMISSIBLE
        TechnicalLeadCompiler.NamespaceAuditResult nonJavaResult = TechnicalLeadCompiler.auditPathsAgainstNamespace(
                null, false, false,
                List.of("docs/architecture/adr-001.md", "README.md")
        );
        assertThat(nonJavaResult.status())
                .as("Non-Java documentation files do not require package namespace and count as ADMISSIBLE")
                .isEqualTo(TechnicalLeadCompiler.NamespaceAuditStatus.ADMISSIBLE);
        assertThat(nonJavaResult.offendingPaths()).isEmpty();
    }

    @Test
    @DisplayName("Searle [D007]: namespace audit violations are formally recorded in the DefectJournal audit ledger")
    void falsifySearle_auditTrailRecordedInDefectJournal() {
        ProjectEntity project = createValidProject("com.eneik.epidemiology");
        UUID featureId = UUID.randomUUID();
        String factoryOffending = "src/main/java/" + TechnicalLeadCompiler.FACTORY_PACKAGE_ROOT.replace('.', '/') + "/service/Secret.java";

        // Violation: recorded as PRODUCT_NAMESPACE_VIOLATION
        TechnicalLeadCompiler.CollisionGuardResult violationResult = compiler.applyCrossEpicCollisionGuardForTest(
                project, featureId, "BARCAN-TAG-02", List.of(factoryOffending)
        );
        assertThat(violationResult.namespaceRefusal()).isTrue();
        assertThat(violationResult.namespaceStatus()).isEqualTo(TechnicalLeadCompiler.NamespaceAuditStatus.VIOLATION);

        verify(defectJournalService).recordDefect(
                eq(project.getId()),
                eq(featureId),
                eq(6), // Charter pattern 6: INDEXICAL_CONTEXT_LOCK (D006)
                eq("CRITICAL"),
                eq("COMPILER"),
                eq("TechnicalLeadCompiler"),
                eq("PRODUCT_NAMESPACE_VIOLATION"),
                anyString(),
                eq(1.0)
        );

        // Unknown namespace: recorded as UNKNOWN_PRODUCT_NAMESPACE
        ProjectEntity unconfiguredProject = createValidProject(null);
        TechnicalLeadCompiler.CollisionGuardResult unknownResult = compiler.applyCrossEpicCollisionGuardForTest(
                unconfiguredProject, featureId, "BARCAN-TAG-02", List.of("src/main/java/com/unknown/Service.java")
        );
        assertThat(unknownResult.namespaceRefusal()).isTrue();
        assertThat(unknownResult.namespaceStatus()).isEqualTo(TechnicalLeadCompiler.NamespaceAuditStatus.UNKNOWN_NAMESPACE);

        verify(defectJournalService).recordDefect(
                eq(unconfiguredProject.getId()),
                eq(featureId),
                eq(6),
                eq("CRITICAL"),
                eq("COMPILER"),
                eq("TechnicalLeadCompiler"),
                eq("UNKNOWN_PRODUCT_NAMESPACE"),
                anyString(),
                eq(1.0)
        );
    }

    // =========================================================================
    // 2. Varzi (D002): AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER
    // =========================================================================

    @Test
    @DisplayName("Varzi [D002]: tasks are actual domain objects inheriting epic identity and lineage from FeatureService")
    void falsifyVarzi_tasksRequireAndInheritEpicIdentityFromFeatureService() {
        ProjectEntity project = createValidProject("com.eneik.epidemiology");
        WishlistEntity wishlist = createValidWishlist(project.getId(), "BARCAN-TAG-02");
        UUID mintedFeatureId = UUID.randomUUID();

        when(wishlistRepository.findById(wishlist.getId())).thenReturn(Optional.of(wishlist));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(featureService.resolveOrCreateFeatureId(wishlist, project.getId())).thenReturn(mintedFeatureId);

        TaskEntity compiledTask = compiler.createTaskFromWishlist(wishlist.getId());

        assertThat(compiledTask).isNotNull();
        assertThat(compiledTask.getFeatureId())
                .as("Task must inherit resolved epic featureId from FeatureService")
                .isEqualTo(mintedFeatureId);
        assertThat(compiledTask.getOriginFeatureId())
                .as("Task without parent originFeatureId inherits resolved featureId as origin")
                .isEqualTo(mintedFeatureId);

        verify(featureService).resolveOrCreateFeatureId(wishlist, project.getId());
    }

    @Test
    @DisplayName("Varzi [D002]: task preserves immutable originFeatureId lineage when wishlist already has origin")
    void falsifyVarzi_preservesOriginLineageWhenWishlistHasOriginFeature() {
        ProjectEntity project = createValidProject("com.eneik.epidemiology");
        WishlistEntity wishlist = createValidWishlist(project.getId(), "BARCAN-TAG-02");
        UUID rootEpicId = UUID.randomUUID();
        UUID branchEpicId = UUID.randomUUID();

        wishlist.setOriginFeatureId(rootEpicId);

        when(wishlistRepository.findById(wishlist.getId())).thenReturn(Optional.of(wishlist));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(featureService.resolveOrCreateFeatureId(wishlist, project.getId())).thenReturn(branchEpicId);

        TaskEntity compiledTask = compiler.createTaskFromWishlist(wishlist.getId());

        assertThat(compiledTask.getFeatureId())
                .as("Feature ID reflects current epic")
                .isEqualTo(branchEpicId);
        assertThat(compiledTask.getOriginFeatureId())
                .as("Origin feature ID must preserve immutable root lineage from wishlist")
                .isEqualTo(rootEpicId);
    }

    // =========================================================================
    // 3. Raz (D006): DZHOZEF_RAZ_01_PROHIBITION_AS_CODE
    // =========================================================================

    @Test
    @DisplayName("Raz [D006]: forbidden scope executes explicit denial path - task blocked, empty JSON scope, explainable reason")
    void falsifyRaz_prohibitionAsCode_violatingScopeHaltsTaskDispatchAndBlocksTask() {
        ProjectEntity project = createValidProject("com.eneik.epidemiology");
        // BARCAN-TAG-11 (frontend) without integration task incorporates hotspot files into scope
        WishlistEntity wishlist = createValidWishlist(project.getId(), "BARCAN-TAG-11");
        UUID mintedFeatureId = UUID.randomUUID();

        // Path is role-relevant for frontend (.svelte) and violates factory package root
        String factoryOffending = "frontend/src/" + TechnicalLeadCompiler.FACTORY_PACKAGE_ROOT.replace('.', '/') + "/internal/FactorySecret.svelte";
        ProjectHotspotFileEntity hotspot = new ProjectHotspotFileEntity();
        hotspot.setProjectId(project.getId());
        hotspot.setFilePath(factoryOffending);

        when(wishlistRepository.findById(wishlist.getId())).thenReturn(Optional.of(wishlist));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectHotspotFileRepository.findByProjectId(project.getId())).thenReturn(List.of(hotspot));
        when(featureService.resolveOrCreateFeatureId(wishlist, project.getId())).thenReturn(mintedFeatureId);

        TaskEntity compiledTask = compiler.createTaskFromWishlist(wishlist.getId());

        // Prohibition as executable code: exclusionary reason preempts queueing
        assertThat(compiledTask.getStatus())
                .as("Task with refused product namespace violation must be BLOCKED, not queued")
                .isEqualTo(TaskStatus.blocked);

        assertThat(compiledTask.getJulesDispatchStatus())
                .as("Dispatch status must record explicit refusal reason")
                .startsWith("BLOCKED: file scope refused (REFUSED_PRODUCT_NAMESPACE_VIOLATION)");

        assertThat(compiledTask.getFileScope())
                .as("Offending paths must be stripped and never permitted into task file scope")
                .doesNotContain(factoryOffending);

        assertThat(compiledTask.getPayload().get("file_scope_status").asText())
                .isEqualTo("REFUSED_PRODUCT_NAMESPACE_VIOLATION");

        assertThat(compiledTask.getPayload().get("refused_paths").toString())
                .contains(factoryOffending);
    }

    @Test
    @DisplayName("Raz [D006]: unknown product namespace executes exclusionary refusal - task blocked with UNKNOWN_PRODUCT_NAMESPACE")
    void falsifyRaz_prohibitionAsCode_unknownNamespaceBlocksTaskFromDispatch() throws Exception {
        // Create temporary workspace with a Java file matching the wish's 'chess' keyword
        java.nio.file.Path tempWs = java.nio.file.Files.createTempDirectory("test-compiler-ws");
        try {
            java.nio.file.Path javaDir = tempWs.resolve("src/main/java/com/unknown");
            java.nio.file.Files.createDirectories(javaDir);
            java.nio.file.Path testJavaFile = javaDir.resolve("ChessMatchService.java");
            java.nio.file.Files.createFile(testJavaFile);

            // Project with unconfigured namespace (null)
            ProjectEntity unconfiguredProject = createValidProject(null);
            unconfiguredProject.setWorkspacePath(tempWs.toString());

            WishlistEntity wishlist = createValidWishlist(unconfiguredProject.getId(), "BARCAN-TAG-02");
            UUID mintedFeatureId = UUID.randomUUID();

            when(wishlistRepository.findById(wishlist.getId())).thenReturn(Optional.of(wishlist));
            when(projectRepository.findById(unconfiguredProject.getId())).thenReturn(Optional.of(unconfiguredProject));
            when(featureService.resolveOrCreateFeatureId(wishlist, unconfiguredProject.getId())).thenReturn(mintedFeatureId);

            TaskEntity compiledTask = compiler.createTaskFromWishlist(wishlist.getId());

            assertThat(compiledTask.getStatus())
                    .as("Unknown namespace must block task execution")
                    .isEqualTo(TaskStatus.blocked);

            assertThat(compiledTask.getJulesDispatchStatus())
                    .isEqualTo("BLOCKED: file scope refused (UNKNOWN_PRODUCT_NAMESPACE)");

            assertThat(compiledTask.getFileScope())
                    .isEqualTo("[]");

            assertThat(compiledTask.getPayload().get("file_scope_status").asText())
                    .isEqualTo("UNKNOWN_PRODUCT_NAMESPACE");

            assertThat(compiledTask.getPayload().get("refused_paths").toString())
                    .contains("src/main/java/com/unknown/ChessMatchService.java");
        } finally {
            // Cleanup temp workspace
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(tempWs)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .map(java.nio.file.Path::toFile)
                        .forEach(java.io.File::delete);
            } catch (Exception ignored) {
            }
        }
    }
}
