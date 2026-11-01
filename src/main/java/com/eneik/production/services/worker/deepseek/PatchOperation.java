package com.eneik.production.services.worker.deepseek;

import java.util.List;

public record PatchOperation(
        String path,
        String operation, // CREATE, MODIFY, DELETE
        String mode,      // FULL_CONTENT, PATCH_BLOCKS
        String content,
        List<PatchBlock> patchBlocks
) {
    public boolean isCreate() {
        return "CREATE".equalsIgnoreCase(operation);
    }

    public boolean isDelete() {
        return "DELETE".equalsIgnoreCase(operation);
    }

    public boolean isPatchBlocks() {
        return "PATCH_BLOCKS".equalsIgnoreCase(mode) || "SEARCH_REPLACE".equalsIgnoreCase(mode);
    }

    public boolean isFullContent() {
        return "FULL_CONTENT".equalsIgnoreCase(mode) || (!isPatchBlocks() && content != null);
    }
}
