package com.eneik.production.services.automerge;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mathematical Conflict Entropy Calculator.
 * Computes Shannon Entropy H(C) over altered files to quantify conflict complexity.
 * Low entropy H(C) < 0.2 -> Trivial orchestrator-owned conflict.
 * High entropy H(C) >= 0.2 -> Complex product code conflict requiring fresh session dispatch.
 */
@Component
public class ConflictEntropyCalculator {

    public record ConflictEntropyResult(
            double shannonEntropy,
            boolean isTrivialOrchestratorConflict,
            long orchestratorFilesCount,
            long productFilesCount
    ) {}

    public ConflictEntropyResult calculateEntropy(List<String> files) {
        if (files == null || files.isEmpty()) {
            return new ConflictEntropyResult(0.0, true, 0, 0);
        }

        long orchestratorCount = files.stream()
                .filter(f -> f.startsWith(".eneik/") || f.equals(".gitignore") || f.endsWith(".md"))
                .count();
        long productCount = files.size() - orchestratorCount;

        double total = files.size();
        double pOrch = orchestratorCount / total;
        double pProd = productCount / total;

        double entropy = 0.0;
        if (pOrch > 0) {
            entropy -= pOrch * (Math.log(pOrch) / Math.log(2));
        }
        if (pProd > 0) {
            entropy -= pProd * (Math.log(pProd) / Math.log(2));
        }

        boolean isTrivial = productCount == 0 || (pOrch >= 0.8 && productCount <= 1);

        return new ConflictEntropyResult(
                Math.round(entropy * 100.0) / 100.0,
                isTrivial,
                orchestratorCount,
                productCount
        );
    }
}
