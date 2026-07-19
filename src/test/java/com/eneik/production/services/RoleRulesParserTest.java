package com.eneik.production.services;

import com.eneik.production.dto.RoleRules;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRulesParserTest {

    private final RoleRulesParser parser = new RoleRulesParser();

    @Test
    void extractsForbiddenFromStructuredSubHeaderList() {
        String markdown = """
                ## КРИТЕРИИ РЕВЬЮ (DEONTIC STATUS)

                ### Obligatory (Обязательно)
                - Do the thing.

                ### Forbidden (Запрещено)
                - Hardcode secrets.
                - Leak internal structure in error messages.

                ### Permitted (Разрешено)
                - Early return.
                """;

        RoleRules rules = parser.parse("BARCAN-TAG-00", markdown);

        assertThat(rules.forbidden()).containsExactly(
                "Hardcode secrets.",
                "Leak internal structure in error messages.");
    }

    @Test
    void extractsForbiddenFromSingleInlineBoldBulletUnderUnrelatedHeader() {
        String markdown = """
                ## ОБРАБОТКА ОШИБОК: ДЕОНТИЧЕСКАЯ КЛАССИФИКАЦИЯ
                - **Forbidden (Запрещено)**: Silent swallow of errors without logging.
                - **Obligatory (Обязательно)**: Log all exceptions with context.
                - **Permitted (Разрешено)**: Retry transient failures up to 3 times.
                """;

        RoleRules rules = parser.parse("BARCAN-TAG-02", markdown);

        assertThat(rules.forbidden()).containsExactly("Silent swallow of errors without logging.");
    }

    @Test
    void extractsForbiddenFromScatteredInlineBulletsWithNoMatchingHeader() {
        String markdown = """
                ## ПРАВИЛА РАБОТЫ (DEONTIC STATUS)

                ### Migration policy
                - **Obligatory**: Every schema change goes through a versioned migration.
                - **Forbidden**: Reusing a migration version number already present in the directory.

                ### OLTP/OLAP separation
                - **Obligatory**: Analytical queries run only against read replicas.
                - **Forbidden**: Direct analyst access to production OLTP databases.
                """;

        RoleRules rules = parser.parse("BARCAN-TAG-08", markdown);

        assertThat(rules.forbidden()).containsExactly(
                "Reusing a migration version number already present in the directory.",
                "Direct analyst access to production OLTP databases.");
    }

    @Test
    void returnsEmptyForbiddenWhenNeitherConventionIsPresent() {
        String markdown = "## Some Role\nNo deontic section here at all.";

        RoleRules rules = parser.parse("BARCAN-TAG-99", markdown);

        assertThat(rules.forbidden()).isEmpty();
    }
}
