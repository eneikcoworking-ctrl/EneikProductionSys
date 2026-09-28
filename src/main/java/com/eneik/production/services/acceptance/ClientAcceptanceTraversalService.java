package com.eneik.production.services.acceptance;

import com.eneik.production.dto.acceptance.ClientAcceptanceTraversalDto;
import com.eneik.production.dto.acceptance.ClientAcceptanceTraversalRequestDto;
import com.eneik.production.models.persistence.ClientAcceptanceTraversalEntity;
import com.eneik.production.repositories.ClientAcceptanceTraversalRepository;
import com.eneik.production.repositories.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Сервис регистрации и выборки институциональных фактов приёмки продукта заказчиком.
 * Closes AGY_ASKS #1, V100, раздел XXIIк.
 *
 * Прикладные философские паттерны:
 * - BARCAN-TAG-12_SOCIAL-CONTRACT:03:dzhon-serl / DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER [D007, Searle]:
 *   регистрация институционального факта приёмки ("X считается Y в контексте C").
 * - BARCAN-TAG-12_SOCIAL-CONTRACT:03:dzhon-serl / DZHON_SERL_07_RIGHTS_DUTIES_MATRIX [D006, Searle]:
 *   авторизованный контур фиксации свидетельств с разделением субъекта обхода (walked_by='client' vs factory).
 */
@Service
public class ClientAcceptanceTraversalService {

    private static final Logger log = LoggerFactory.getLogger(ClientAcceptanceTraversalService.class);
    public static final String DEFAULT_WALKED_BY = "client";

    private final ClientAcceptanceTraversalRepository traversalRepository;
    private final ProjectRepository projectRepository;

    public ClientAcceptanceTraversalService(ClientAcceptanceTraversalRepository traversalRepository,
                                           ProjectRepository projectRepository) {
        this.traversalRepository = traversalRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Регистрирует свидетельство обхода цепочки ценности (V100 append-only log).
     */
    @Transactional
    public ClientAcceptanceTraversalDto recordTraversal(UUID projectId, ClientAcceptanceTraversalRequestDto request) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
        if (!projectRepository.existsById(projectId)) {
            throw new NoSuchElementException("Project not found: " + projectId);
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        if (request.profileId() == null || request.profileId().trim().isEmpty()) {
            throw new IllegalArgumentException("profileId must not be blank");
        }
        if (request.actor() == null || request.actor().trim().isEmpty()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        if (request.link() == null || request.link().trim().isEmpty()) {
            throw new IllegalArgumentException("link must not be blank");
        }

        String rawWalkedBy = request.walkedBy();
        String walkedBy = (rawWalkedBy != null && !rawWalkedBy.trim().isEmpty())
                ? rawWalkedBy.trim()
                : DEFAULT_WALKED_BY;

        // Нормализация и ограничение длины по схеме таблицы V100
        String profileId = truncate(request.profileId().trim(), 64);
        String actor = truncate(request.actor().trim(), 64);
        String link = truncate(request.link().trim(), 512);
        walkedBy = truncate(walkedBy, 32);
        String evidence = request.evidence() != null ? truncate(request.evidence().trim(), 2000) : null;
        String instanceUrl = request.instanceUrl() != null ? truncate(request.instanceUrl().trim(), 512) : null;

        ClientAcceptanceTraversalEntity entity = new ClientAcceptanceTraversalEntity();
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        entity.setProjectId(projectId);
        entity.setProfileId(profileId);
        entity.setActor(actor);
        entity.setLink(link);
        entity.setWalkedBy(walkedBy);
        entity.setEvidence(evidence);
        entity.setInstanceUrl(instanceUrl);
        entity.setTraversedAt(Instant.now());

        ClientAcceptanceTraversalEntity saved = traversalRepository.save(entity);

        log.info("[PROJECT:{}] [ACCEPTANCE_TRAVERSAL][INSTITUTIONAL_FACT] Recorded acceptance traversal {} by actor '{}' (profileId: '{}', link: '{}', walkedBy: '{}', instanceUrl: '{}')",
                projectId, saved.getId(), actor, profileId, link, walkedBy, instanceUrl);

        return toDto(saved);
    }

    /**
     * Возвращает список всех зарегистрированных обходов проекта, опционально фильтруя по субъекту обхода (walkedBy).
     */
    @Transactional(readOnly = true)
    public List<ClientAcceptanceTraversalDto> listTraversals(UUID projectId, String walkedBy) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
        if (!projectRepository.existsById(projectId)) {
            throw new NoSuchElementException("Project not found: " + projectId);
        }

        List<ClientAcceptanceTraversalEntity> traversals;
        if (walkedBy != null && !walkedBy.trim().isEmpty()) {
            traversals = traversalRepository.findByProjectIdAndWalkedByIgnoreCaseOrderByTraversedAtDesc(projectId, walkedBy.trim());
        } else {
            traversals = traversalRepository.findByProjectIdOrderByTraversedAtDesc(projectId);
        }

        return traversals.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Возвращает количество подтвержденных обходов заказчиком (walked_by='client').
     */
    @Transactional(readOnly = true)
    public long countClientTraversals(UUID projectId) {
        if (projectId == null) {
            return 0L;
        }
        return traversalRepository.countByProjectIdAndWalkedByIgnoreCase(projectId, DEFAULT_WALKED_BY);
    }

    private ClientAcceptanceTraversalDto toDto(ClientAcceptanceTraversalEntity entity) {
        return new ClientAcceptanceTraversalDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getProfileId(),
                entity.getActor(),
                entity.getLink(),
                entity.getTraversedAt(),
                entity.getWalkedBy(),
                entity.getEvidence(),
                entity.getInstanceUrl()
        );
    }

    private static String truncate(String val, int maxLength) {
        if (val == null || val.length() <= maxLength) {
            return val;
        }
        return val.substring(0, maxLength);
    }
}
