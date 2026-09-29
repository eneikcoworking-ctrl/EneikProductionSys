package com.eneik.production.services.jules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Фальсифицирующий тест-заслон для JulesRoleCapabilities.
 * <p>
 * Философский RAG-каркас:
 * - BARCAN-TAG-02_RIGID-DESIGNATOR:01:sol-kripke / SOL_KRIPKE_01_RIGID_API_REFERENT (D001, Kripke):
 *   Жёсткая десигнация 13 BARCAN-тегов ролей (от BARCAN-TAG-00 до BARCAN-TAG-12). Теги и каноническая строка
 *   ALL_CAPABILITIES сохраняют тождество и неизменяемый референт во всех потребителях фабрики.
 * - BARCAN-TAG-10_DEONTIC-PROHIBITION:03:dzhozef-raz / DZHOZEF_RAZ_01_PROHIBITION_AS_CODE (D006, Raz):
 *   Предикат закрытого мира isKnownRole. Чётко демаркирует границу легитимных ролей: всё, что не входит
 *   в закрытый список 13 канонических тегов (несуществующие теги, опечатки, нижний регистр, null, пустота),
 *   безоговорочно отвергается как неизвестная роль.
 */
@DisplayName("Falsification: JulesRoleCapabilities Rigid Designation & Closed-World Prohibition (D001 Kripke, D006 Raz)")
class JulesRoleCapabilitiesFalsificationTest {

    @Nested
    @DisplayName("KRIPKE_01: Жёсткая десигнация ролевого словаря BARCAN (D001)")
    class KripkeRigidDesignationTests {

        @Test
        @DisplayName("KRIPKE_01: Словарь ролей содержит ровно 13 уникальных жестких десигнаторов в каноническом порядке")
        void allThirteenBarcanRoleTagsPreservedRigidly() {
            List<String> tags = JulesRoleCapabilities.ALL_ROLE_TAGS;

            assertThat(tags)
                    .as("Список ролей должен содержать строго 13 BARCAN-тегов")
                    .hasSize(13);

            // Проверка строгого порядка и тождества каждого десигнатора
            for (int i = 0; i <= 12; i++) {
                String expectedTag = String.format("BARCAN-TAG-%02d", i);
                assertThat(tags.get(i))
                        .as("Тег с индексом %d должен быть жестким десигнатором %s", i, expectedTag)
                        .isEqualTo(expectedTag);
            }

            // Проверка отсутствия дубликатов
            assertThat(new HashSet<>(tags))
                    .as("Все 13 тегов должны быть уникальными")
                    .hasSize(13);
        }

        @Test
        @DisplayName("KRIPKE_01: Каноническая строка ALL_CAPABILITIES детерминированно объединяет все 13 тегов через запятую")
        void canonicalCapabilitiesStringIsDeterministicAndConsistent() {
            String canonical = JulesRoleCapabilities.canonicalCapabilities();
            String constant = JulesRoleCapabilities.ALL_CAPABILITIES;

            assertThat(canonical)
                    .as("canonicalCapabilities() должен возвращать ALL_CAPABILITIES")
                    .isEqualTo(constant);

            String expectedJoined = String.join(",", JulesRoleCapabilities.ALL_ROLE_TAGS);
            assertThat(constant)
                    .as("ALL_CAPABILITIES должен в точности соответствовать объединению 13 тегов через запятую")
                    .isEqualTo(expectedJoined);

            assertThat(constant)
                    .startsWith("BARCAN-TAG-00")
                    .endsWith("BARCAN-TAG-12");
        }

        @Test
        @DisplayName("KRIPKE_01: Неизменяемость ролевого словаря ALL_ROLE_TAGS (защита от мутации в рантайме)")
        void allRoleTagsListIsImmutable() {
            List<String> tags = JulesRoleCapabilities.ALL_ROLE_TAGS;

            assertThatThrownBy(() -> tags.add("BARCAN-TAG-13"))
                    .as("Попытка добавления в ALL_ROLE_TAGS должна пресекаться UnsupportedOperationException")
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThatThrownBy(() -> tags.remove(0))
                    .as("Попытка удаления из ALL_ROLE_TAGS должна пресекаться UnsupportedOperationException")
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("RAZ_01: Предикат закрытого мира isKnownRole (D006)")
    class RazClosedWorldProhibitionTests {

        @Test
        @DisplayName("RAZ_01: Все 13 канонических тегов признаются известными ролями (isKnownRole == true)")
        void allThirteenTagsAreKnownRoles() {
            for (String tag : JulesRoleCapabilities.ALL_ROLE_TAGS) {
                assertThat(JulesRoleCapabilities.isKnownRole(tag))
                        .as("Канонический тег %s обязан признаваться известной ролью", tag)
                        .isTrue();
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "   ",
                "BARCAN-TAG-13",
                "BARCAN-TAG-99",
                "BARCAN-TAG-0",
                "barcan-tag-00",
                "barcan-tag-05",
                "BARCAN_TAG_00",
                "ROLE_ADMIN",
                "DEVELOPER",
                "UNKNOWN"
        })
        @DisplayName("RAZ_01: Нелегитимные, искаженные, пустые или внешние теги отвергаются предикатом закрытого мира (isKnownRole == false)")
        void invalidAndUnknownTagsAreRefused(String invalidTag) {
            assertThat(JulesRoleCapabilities.isKnownRole(invalidTag))
                    .as("Нелегитимный тег '%s' обязан отвергаться предикатом закрытого мира", invalidTag)
                    .isFalse();
        }

        @Test
        @DisplayName("RAZ_01: Передача null в isKnownRole выбрасывает NPE из-за контракта List.of().contains(null)")
        void nullTagThrowsNullPointerExceptionDueToImmutableListContract() {
            assertThatThrownBy(() -> JulesRoleCapabilities.isKnownRole(null))
                    .as("Вызов List.of().contains(null) по спецификации JDK выбрасывает NPE")
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
