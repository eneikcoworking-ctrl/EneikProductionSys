package com.eneik.production.services.design.sovereign;

import com.eneik.production.services.design.sovereign.domain.ContrastRule;
import com.eneik.production.services.design.sovereign.domain.DesignTokens;
import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.domain.TypographyConstraint;
import com.eneik.production.services.design.sovereign.service.DefaultDesignTokenCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DesignTokenCompilerTest {

    private DefaultDesignTokenCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DefaultDesignTokenCompiler();
    }

    @Test
    void whenUnknownArchetypeGiven_thenFallbackToDefaultArchetype() {
        ProductArchetype archetypeNull = compiler.classifyArchetype(null);
        assertEquals(ProductArchetype.DEFAULT, archetypeNull);

        ProductArchetype archetypeUnknown = compiler.classifyArchetype("UNKNOWN_SOMETHING_123");
        assertEquals(ProductArchetype.DEFAULT, archetypeUnknown);

        DesignTokens tokensNull = compiler.compileTokensAggregate((String) null);
        assertEquals(ProductArchetype.DEFAULT, tokensNull.archetype());

        DesignTokens tokensUnknown = compiler.compileTokensAggregate("unknown_archetype_xyz");
        assertEquals(ProductArchetype.DEFAULT, tokensUnknown.archetype());
    }

    @Test
    void whenValidArchetypeGiven_thenReturnsValidDesignTokensAndTypographyConstraints() {
        DesignTokens devTokens = compiler.compileTokensAggregate("DEV_TOOLS");
        assertEquals(ProductArchetype.DEV_TOOLS, devTokens.archetype());
        assertFalse(devTokens.typography().isEmpty());

        for (TypographyConstraint constraint : devTokens.typography()) {
            assertNotNull(constraint.fontSizeCategory());
            assertNotNull(constraint.minSize());
            assertNotNull(constraint.preferredSize());
            assertNotNull(constraint.maxSize());
            assertTrue(constraint.clampExpression().startsWith("clamp("));
        }
    }

    @Test
    void whenDesignTokensCompiled_thenContrastRulesMeetWcagAaaMinimums() {
        for (ProductArchetype archetype : ProductArchetype.values()) {
            DesignTokens tokens = compiler.compileTokensAggregate(archetype);
            ContrastRule rule = tokens.contrastRule();

            assertNotNull(rule);
            assertTrue(rule.contrastRatio() >= 7.0,
                    "Contrast ratio for archetype " + archetype + " must be >= 7.0 (WCAG AAA), got " + rule.contrastRatio());
            assertTrue(rule.meetsWcagAaa());
        }
    }

    @Test
    void testContrastRatioCalculation() {
        // Black on white contrast is ~21:1
        double ratioBW = ContrastRule.calculateContrastRatio("#000000", "#FFFFFF");
        assertTrue(ratioBW >= 20.0, "Black on white contrast ratio should be ~21:1");

        // White on dark obsidian #08090C contrast is > 18:1
        ContrastRule obsidianRule = ContrastRule.calculate("#FFFFFF", "#08090C");
        assertTrue(obsidianRule.contrastRatio() >= 7.0);
        assertTrue(obsidianRule.meetsWcagAaa());
    }
}
