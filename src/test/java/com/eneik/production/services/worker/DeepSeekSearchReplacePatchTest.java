package com.eneik.production.services.worker;

import com.eneik.production.services.worker.deepseek.PatchBlock;
import com.eneik.production.services.worker.deepseek.PatchOperation;
import com.eneik.production.services.worker.worktree.GitWorktreeExecutionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekSearchReplacePatchTest {

    private final GitWorktreeExecutionManager worktreeManager = new GitWorktreeExecutionManager();

    @Test
    @DisplayName("Surgical PATCH_BLOCKS replacement preserves surrounding lines in 300+ line file")
    void testSearchReplaceOnLargeFile(@TempDir Path tempDir) throws IOException {
        Path largeFile = tempDir.resolve("LargeService.java");

        // Generate 350-line file
        StringBuilder sb = new StringBuilder();
        sb.append("package com.example;\n\npublic class LargeService {\n");
        for (int i = 1; i <= 350; i++) {
            if (i == 175) {
                sb.append("    // TARGET METHOD TO REPLACE\n");
                sb.append("    public String calculateOldMetric() {\n");
                sb.append("        return \"OLD_VALUE\";\n");
                sb.append("    }\n");
            } else {
                sb.append("    public void helperMethod").append(i).append("() {}\n");
            }
        }
        sb.append("}\n");
        Files.writeString(largeFile, sb.toString(), StandardCharsets.UTF_8);

        // Define surgical search and replace
        String searchBlock = "    // TARGET METHOD TO REPLACE\n" +
                "    public String calculateOldMetric() {\n" +
                "        return \"OLD_VALUE\";\n" +
                "    }";

        String replaceBlock = "    // TARGET METHOD REPLACED SURGICALLY\n" +
                "    public String calculateOldMetric() {\n" +
                "        return \"NEW_SUPERIOR_VALUE\";\n" +
                "    }";

        PatchOperation op = new PatchOperation(
                "LargeService.java",
                "MODIFY",
                "PATCH_BLOCKS",
                null,
                List.of(new PatchBlock(searchBlock, replaceBlock))
        );

        worktreeManager.applyPatches(tempDir, List.of(op));

        String updatedContent = Files.readString(largeFile, StandardCharsets.UTF_8);
        assertTrue(updatedContent.contains("NEW_SUPERIOR_VALUE"));
        assertTrue(updatedContent.contains("// TARGET METHOD REPLACED SURGICALLY"));
        assertFalse(updatedContent.contains("OLD_VALUE"));
        assertTrue(updatedContent.contains("public void helperMethod1() {}"));
        assertTrue(updatedContent.contains("public void helperMethod350() {}"));
    }

    @Test
    @DisplayName("FULL_CONTENT creates new file with parent directories")
    void testFullContentCreatesNewFile(@TempDir Path tempDir) throws IOException {
        String newCode = "package com.example.sub;\n\npublic class NewService {}";
        PatchOperation op = new PatchOperation(
                "src/main/java/com/example/sub/NewService.java",
                "CREATE",
                "FULL_CONTENT",
                newCode,
                List.of()
        );

        worktreeManager.applyPatches(tempDir, List.of(op));

        Path target = tempDir.resolve("src/main/java/com/example/sub/NewService.java");
        assertTrue(Files.exists(target));
        assertEquals(newCode, Files.readString(target, StandardCharsets.UTF_8));
    }
}
