package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.GeminiContextService;
import com.eneik.production.services.RoleCapabilityLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Unified Role Charter and Memorilla (K=16) context assembler for Sovereign Workers
 * (DeepSeekWorktreeExecutionEngine, LocalAgyWorktreeExecutionEngine).
 *
 * <p>Enforces:
 * <ul>
 *   <li>Dynamic BARCAN role adoption at dispatch time ({@code GILBERT_RAYL_02_INFERENTIAL_SCOREBOARD}, D001)</li>
 *   <li>Stanford Memorilla K=16 orthogonal latent semantic slots ({@code ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE}, D014)</li>
 *   <li>Strict Tarski L0 stratification ({@code ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK}, D010):
 *       {@link TargetContext#ORCHESTRATOR_SYSTEM} uses {@code buildLatentSemanticMemoryBlock}, whereas
 *       {@link TargetContext#PRODUCT_CODEBASE} and any {@code null}/undetermined context fail-safe to
 *       {@code buildProductWorkerLatentMemoryBlock} (zero leakage of {@code observer_log} or {@code factory_mechanisms}).</li>
 *   <li>Graceful degradation: never throws and never truncates {@code task.getDescription()}.</li>
 * </ul>
 */
@Service
public class SovereignWorkerContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(SovereignWorkerContextAssembler.class);

    public static final int MAX_ROLE_CHARTER_CHARS = 4000;
    public static final int MAX_QUERY_DESCRIPTION_CHARS = 600;
    public static final String SECTION_HEADER = "### ACTIVE ROLE CHARTER & MEMORILLA DOCTRINE MEMORY (K=16)";

    private final GeminiContextService geminiContextService;
    private final RoleCapabilityLoader roleCapabilityLoader;

    @Autowired
    public SovereignWorkerContextAssembler(
            @Autowired(required = false) GeminiContextService geminiContextService,
            @Autowired(required = false) RoleCapabilityLoader roleCapabilityLoader) {
        this.geminiContextService = geminiContextService;
        this.roleCapabilityLoader = roleCapabilityLoader;
    }

    /**
     * Assembles the dynamic Role Charter and Tarski-stratified Memorilla K=16 doctrine block for the given task.
     * Never throws and never mutates or truncates {@code task.getDescription()}.
     */
    public String assemble(TaskEntity task, String mode) {
        if (task == null) {
            return "";
        }

        RoleEntity role = task.getRole();
        String roleTag = (role != null && role.getTag() != null && !role.getTag().isBlank())
                ? role.getTag().trim()
                : null;
        String roleDescription = (role != null && role.getDescription() != null && !role.getDescription().isBlank())
                ? role.getDescription().trim()
                : null;
        TargetContext targetContext = task.getTargetContext();

        String charterText = loadRoleCharterSafely(task, roleTag);
        String retrievalQuery = buildRetrievalQuery(task, mode, roleTag, roleDescription);
        String memorillaBlock = retrieveMemorillaSafely(task, role, targetContext, retrievalQuery);

        int slotCount = countMemorillaSlots(memorillaBlock);
        boolean hasCharter = charterText != null && !charterText.isBlank();
        boolean hasMemorilla = memorillaBlock != null && !memorillaBlock.isBlank();

        if (!hasCharter && !hasMemorilla) {
            log.info("SovereignWorkerContextAssembler: task={} role={} targetContext={} slots=0 charterChars=0 totalChars=0",
                    task.getId(), roleTag != null ? roleTag : "NONE", targetContext);
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(SECTION_HEADER).append("\n");
        if (roleTag != null) {
            sb.append("Active Role: ").append(roleTag);
            if (roleDescription != null) {
                sb.append(" — ").append(roleDescription);
            }
            sb.append("\n");
        }

        if (hasCharter) {
            sb.append("\n#### Role Charter (").append(roleTag != null ? roleTag : "UNKNOWN").append(")\n");
            sb.append(charterText).append("\n");
        }

        if (hasMemorilla) {
            sb.append("\n").append(memorillaBlock.stripTrailing()).append("\n");
        }

        String assembled = sb.toString();
        log.info("SovereignWorkerContextAssembler: task={} role={} targetContext={} slots={} charterChars={} totalChars={}",
                task.getId(), roleTag != null ? roleTag : "NONE", targetContext,
                slotCount, hasCharter ? charterText.length() : 0, assembled.length());
        return assembled;
    }

    private String loadRoleCharterSafely(TaskEntity task, String roleTag) {
        if (roleCapabilityLoader == null || roleTag == null || task.isCarrier()) {
            return "";
        }
        try {
            String rawCharter = roleCapabilityLoader.loadRawCharter(roleTag);
            if (rawCharter == null || rawCharter.isBlank()) {
                return "";
            }
            String trimmed = rawCharter.strip();
            if (trimmed.length() <= MAX_ROLE_CHARTER_CHARS) {
                return trimmed;
            }
            String suffix = "\n... [charter capped at " + MAX_ROLE_CHARTER_CHARS + " chars]";
            int cut = Math.max(0, MAX_ROLE_CHARTER_CHARS - suffix.length());
            return trimmed.substring(0, cut).stripTrailing() + suffix;
        } catch (Exception e) {
            log.warn("SovereignWorkerContextAssembler: could not load role charter for task={} role={}: {}",
                    task.getId(), roleTag, e.getMessage());
            return "";
        }
    }

    private String retrieveMemorillaSafely(TaskEntity task, RoleEntity role, TargetContext targetContext, String retrievalQuery) {
        if (geminiContextService == null || retrievalQuery == null || retrievalQuery.isBlank()) {
            return "";
        }
        try {
            String block;
            if (targetContext == TargetContext.ORCHESTRATOR_SYSTEM) {
                block = geminiContextService.buildLatentSemanticMemoryBlock(retrievalQuery);
            } else {
                // Fail-safe Tarski L0 demarcation: PRODUCT_CODEBASE, UNDETERMINED, or null NEVER touch factory metalanguage
                block = geminiContextService.buildProductWorkerLatentMemoryBlock(role, retrievalQuery);
            }
            return block != null ? block : "";
        } catch (Exception e) {
            log.warn("SovereignWorkerContextAssembler: Memorilla retrieval failed for task={} role={} targetContext={}: {}",
                    task.getId(), role != null ? role.getTag() : "NONE", targetContext, e.getMessage());
            return "";
        }
    }

    static String buildRetrievalQuery(TaskEntity task, String mode, String roleTag, String roleDescription) {
        StringBuilder q = new StringBuilder();
        if (mode != null && !mode.isBlank()) {
            q.append("mode=").append(mode.trim()).append(" ");
        }
        if (roleTag != null && !roleTag.isBlank()) {
            q.append("roleTag=").append(roleTag.trim()).append(" ");
        }
        if (roleDescription != null && !roleDescription.isBlank()) {
            q.append("role=").append(roleDescription.trim()).append(" ");
        }
        if (task.getTitle() != null && !task.getTitle().isBlank()) {
            q.append("title=").append(task.getTitle().trim()).append(" ");
        }
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            String desc = task.getDescription().strip().replaceAll("\\s+", " ");
            if (desc.length() > MAX_QUERY_DESCRIPTION_CHARS) {
                desc = desc.substring(0, MAX_QUERY_DESCRIPTION_CHARS);
            }
            q.append("task=").append(desc).append(" ");
        }
        if (task.getFileScope() != null && !task.getFileScope().isBlank() && !"[]".equals(task.getFileScope().trim())) {
            q.append("fileScope=").append(task.getFileScope().trim());
        }
        return q.toString().trim();
    }

    static int countMemorillaSlots(String memorillaBlock) {
        if (memorillaBlock == null || memorillaBlock.isBlank()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        String marker = "[Slot #";
        while ((idx = memorillaBlock.indexOf(marker, idx)) >= 0) {
            count++;
            idx += marker.length();
        }
        return count;
    }
}
