package com.eneik.production.services.worker.deepseek;

import java.util.List;

public record DeepSeekResponse(
        String summary,
        String rationale,
        ScopeStatus scopeStatus,
        List<String> missingFiles,
        List<PatchOperation> files,
        int cachedTokens,
        int promptTokens,
        int completionTokens,
        String modelName,
        String rawContent
) {}
