package com.eneik.production.services.monitor;

import org.springframework.stereotype.Component;

@Component
public class RiskLevelCalculator {

    /**
     * Calculates risk level based on PR statistics and CI status.
     *
     * Formula:
     * low: linesChanged < 50 AND hasTestChanges=true AND ciStatus='passing' AND NOT touchesCriticalPath
     * high: linesChanged > 300 OR ciStatus='failing' OR touchesCriticalPath
     * medium: everything else
     *
     * @param linesChanged Total lines changed in the PR
     * @param filesChanged Total files changed in the PR
     * @param hasTestChanges Whether the PR contains changes to test files
     * @param ciStatus CI status (e.g., 'passing', 'failing')
     * @param touchesCriticalPath Whether the PR touches critical paths (ClaimService, LeaseWatchdogService, GateOrchestrator)
     * @return "low", "medium", or "high"
     */
    public String calculate(int linesChanged, int filesChanged, boolean hasTestChanges, String ciStatus, boolean touchesCriticalPath) {
        if (linesChanged > 300 || "failing".equals(ciStatus) || touchesCriticalPath) {
            return "high";
        }

        if (linesChanged < 50 && hasTestChanges && "passing".equals(ciStatus) && !touchesCriticalPath) {
            return "low";
        }

        return "medium";
    }
}
