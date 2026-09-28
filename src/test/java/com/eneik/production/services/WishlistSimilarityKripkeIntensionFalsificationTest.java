package com.eneik.production.services;

import com.eneik.production.models.persistence.WishlistEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Фальсифицирующий замер Ступени 2 для V132 / areWishlistItemsSimilar:
 * жесткая десигнация идентичности требований и JTBD-семантики (SOL_KRIPKE_03 / D001)
 * и эмпирическая проверка дедупликации (LYUDVIG_VITGENSHTEYN_14 / D013).
 *
 * Proof obligations:
 * 1. Инвариант декомпозиции одного брифа (Kripke Rigid Designation):
 *    Срезы одного родительского брифа (originWishlistId совпадает) НИКОГДА не признаются дубликатами,
 *    даже при 100% текстуальном совпадении JTBD или заголовков (структурный запрет деградации декомпозиции).
 * 2. JTBD и критерии приемки как истинный референт (Kripke Intension Compatibility):
 *    Для скомпилированных срезов (compiledByRole != null) сгенерированный шаблонный заголовок
 *    ("Internal work item N...") игнорируется, а сравнение идет строго по JTBD и acceptanceCriteria.
 * 3. Столбцовая идентичность находок (Wittgenstein Anti-Mirror Telemetry):
 *    Находки о разных задачах (sourceTaskId различен) никогда не объединяются, даже при идентичном тексте шаблона.
 * 4. Граничное условие порога Жаккара:
 *    Порог сходства равен ровно 0.25.
 */
class WishlistSimilarityKripkeIntensionFalsificationTest {

    private static final UUID BRIEF_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRIEF_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("SOL_KRIPKE_03: Срезы одного брифа структурно защищены от слияния даже при идентичном JTBD")
    void sameOriginBriefSlicesAreNeverDuplicatesEvenWithIdenticalJtbd() {
        // Ситуация: компилятор создал две разные задачи под один бриф, случайно сгенерировав схожий JTBD.
        // Инвариант Kripke: совпадение originWishlistId означает, что это разные части одного задания.
        String identicalJtbd = "When authenticating users, I want multi-factor authentication enforced, so that access is secure.";
        WishlistEntity slice1 = new WishlistEntity();
        slice1.setId(UUID.randomUUID());
        slice1.setOriginWishlistId(BRIEF_A);
        slice1.setCompiledByRole("BARCAN-TAG-01");
        slice1.setContent("Internal work item 1 (BARCAN-TAG-01) from wishlist " + BRIEF_A + ": MFA Backend");
        slice1.setJtbd(identicalJtbd);

        WishlistEntity slice2 = new WishlistEntity();
        slice2.setId(UUID.randomUUID());
        slice2.setOriginWishlistId(BRIEF_A);
        slice2.setCompiledByRole("BARCAN-TAG-02");
        slice2.setContent("Internal work item 2 (BARCAN-TAG-02) from wishlist " + BRIEF_A + ": MFA Frontend");
        slice2.setJtbd(identicalJtbd);

        assertFalse(ProjectFlowService.areWishlistItemsSimilar(slice1, slice2),
                "Разные срезы одного брифа НИКОГДА не должны объединяться (V132 / Charter invariant 14)");
    }

    @Test
    @DisplayName("SOL_KRIPKE_03: Идентичность срезов определяется связкой JTBD + AcceptanceCriteria, а не заголовком")
    void compiledSlicesComparedOnJtbdAndAcceptanceCriteriaCombined() {
        WishlistEntity sliceA = new WishlistEntity();
        sliceA.setId(UUID.randomUUID());
        sliceA.setOriginWishlistId(BRIEF_A);
        sliceA.setCompiledByRole("BARCAN-TAG-03");
        sliceA.setContent("Boilerplate Header Alpha from brief " + BRIEF_A);
        sliceA.setJtbd("When processing payments, I want webhook idempotency checked");
        sliceA.setAcceptanceCriteria("Must record idempotency key in database table");

        WishlistEntity sliceB = new WishlistEntity();
        sliceB.setId(UUID.randomUUID());
        sliceB.setOriginWishlistId(BRIEF_B);
        sliceB.setCompiledByRole("BARCAN-TAG-04");
        sliceB.setContent("Completely Different Header Beta from brief " + BRIEF_B);
        sliceB.setJtbd("When processing payments, I want webhook idempotency checked");
        sliceB.setAcceptanceCriteria("Must record idempotency key in database table");

        // Разные брифы, совершенно разные заголовки, но идентичные требования (JTBD + Acceptance) -> дубликат
        assertTrue(ProjectFlowService.areWishlistItemsSimilar(sliceA, sliceB));
    }

    @Test
    @DisplayName("SOL_KRIPKE_03: При отсутствии JTBD и acceptanceCriteria скомпилированный срез откатывается к content")
    void compiledSliceWithoutJtbdFallsBackToContent() {
        WishlistEntity sliceA = new WishlistEntity();
        sliceA.setId(UUID.randomUUID());
        sliceA.setOriginWishlistId(BRIEF_A);
        sliceA.setCompiledByRole("BARCAN-TAG-05");
        sliceA.setContent("Urgent security patch: upgrade Jackson databind library to mitigate vulnerability");
        sliceA.setJtbd(null);
        sliceA.setAcceptanceCriteria("");

        WishlistEntity sliceB = new WishlistEntity();
        sliceB.setId(UUID.randomUUID());
        sliceB.setOriginWishlistId(BRIEF_B);
        sliceB.setCompiledByRole("BARCAN-TAG-05");
        sliceB.setContent("Urgent security patch: upgrade Jackson databind library to mitigate vulnerability");
        sliceB.setJtbd("");
        sliceB.setAcceptanceCriteria(null);

        assertTrue(ProjectFlowService.areWishlistItemsSimilar(sliceA, sliceB));
    }

    @Test
    @DisplayName("LYUDVIG_VITGENSHTEYN_14: Находки об одной и той же задаче с похожим текстом дедуплицируются")
    void findingsAboutSameTaskWithSimilarTextAreMerged() {
        UUID taskId = UUID.randomUUID();
        WishlistEntity finding1 = new WishlistEntity();
        finding1.setId(UUID.randomUUID());
        finding1.setSourceTaskId(taskId);
        finding1.setContent("Planned work failed: test execution timed out on maven test compile");

        WishlistEntity finding2 = new WishlistEntity();
        finding2.setId(UUID.randomUUID());
        finding2.setSourceTaskId(taskId);
        finding2.setContent("Planned work failed: test execution timed out on maven surefire test run");

        assertTrue(ProjectFlowService.areWishlistItemsSimilar(finding1, finding2));
    }

    @Test
    @DisplayName("LYUDVIG_VITGENSHTEYN_14: Порог сходства Жаккара строго отсекает совпадения ниже 0.25")
    void jaccardSimilarityThresholdStrictlyEnforced() {
        // Текст 1: "alpha beta gamma delta epsilon zeta" (6 токенов)
        // Текст 2: "alpha beta eta theta iota kappa" (6 токенов)
        // Пересечение: {alpha, beta} (2 токена)
        // Объединение: 10 токенов
        // Коэффициент: 2 / 10 = 0.20 < 0.25 -> НЕ дубликат
        WishlistEntity item1 = new WishlistEntity();
        item1.setId(UUID.randomUUID());
        item1.setContent("alpha beta gamma delta epsilon zeta");

        WishlistEntity item2 = new WishlistEntity();
        item2.setId(UUID.randomUUID());
        item2.setContent("alpha beta eta theta iota kappa");

        assertFalse(ProjectFlowService.areWishlistItemsSimilar(item1, item2),
                "Сходство 0.20 должно быть ниже порога 0.25");

        // Добавим еще одно совпадение: "gamma"
        // Пересечение: {alpha, beta, gamma} (3 токена)
        // Объединение: 9 токенов
        // Коэффициент: 3 / 9 = 0.333 >= 0.25 -> Дубликат!
        WishlistEntity item3 = new WishlistEntity();
        item3.setId(UUID.randomUUID());
        item3.setContent("alpha beta gamma eta theta iota");

        assertTrue(ProjectFlowService.areWishlistItemsSimilar(item1, item3),
                "Сходство 0.33 должно быть выше порога 0.25");
    }
}
