package com.eneik.production.dto.acceptance;

/**
 * Запрос на регистрацию обхода заказчика или фабрики в контуре приёмки (AGY_ASKS #1, V100).
 *
 * Паттерны:
 * - DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER [D007, Searle]: регистрация институционального факта приёмки.
 * - DZHON_SERL_07_RIGHTS_DUTIES_MATRIX [D006, Searle]: авторизованный контур фиксации свидетельств.
 */
public record ClientAcceptanceTraversalRequestDto(
        String profileId,
        String actor,
        String link,
        String walkedBy,
        String evidence,
        String instanceUrl
) {
    public ClientAcceptanceTraversalRequestDto(String profileId, String actor, String link) {
        this(profileId, actor, link, null, null, null);
    }
}
