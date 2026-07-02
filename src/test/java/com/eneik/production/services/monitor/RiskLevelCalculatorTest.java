package com.eneik.production.services.monitor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RiskLevelCalculatorTest {

    private final RiskLevelCalculator calculator = new RiskLevelCalculator();

    @Test
    public void testLowRiskBoundary() {
        // low: linesChanged < 50 AND hasTestChanges=true AND ciStatus='passing' AND NOT touchesCriticalPath
        assertEquals("low", calculator.calculate(49, 1, true, "passing", false));
    }

    @Test
    public void testMediumRiskLinesBoundary() {
        // linesChanged < 50 is required for low, so 50 should be medium
        assertEquals("medium", calculator.calculate(50, 1, true, "passing", false));
    }

    @Test
    public void testMediumRiskNoTests() {
        // hasTestChanges=true is required for low
        assertEquals("medium", calculator.calculate(45, 1, false, "passing", false));
    }

    @Test
    public void testHighRiskCiFailing() {
        // ciStatus='failing' -> high
        assertEquals("high", calculator.calculate(10, 1, true, "failing", false));
    }

    @Test
    public void testHighRiskTouchesCriticalPath() {
        // touchesCriticalPath=true -> high
        assertEquals("high", calculator.calculate(10, 1, true, "passing", true));
    }

    @Test
    public void testMediumRiskHighLinesBoundary() {
        // high: linesChanged > 300, so 300 should be medium (if other low conditions aren't met)
        assertEquals("medium", calculator.calculate(300, 1, true, "passing", false));
    }

    @Test
    public void testHighRiskAboveHighLinesBoundary() {
        // linesChanged > 300 -> high
        assertEquals("high", calculator.calculate(301, 1, true, "passing", false));
    }

    @Test
    public void testMediumRiskGeneral() {
        assertEquals("medium", calculator.calculate(100, 1, true, "passing", false));
    }
}
