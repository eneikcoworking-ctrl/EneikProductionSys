package com.eneik.production.kaizen.service;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Defect Journal Service - Silent under-the-hood telemetry collector for all system anomalies,
 * queue stalls, Six Sigma DPMO spikes, and TOC Sentinel events.
 */
@Service
public class DefectJournalService {

    private static final Logger log = LoggerFactory.getLogger(DefectJournalService.class);

    private final DefectJournalRepository defectJournalRepository;

    public DefectJournalService(DefectJournalRepository defectJournalRepository) {
        this.defectJournalRepository = defectJournalRepository;
        log.info("[DEFECT-JOURNAL] Under-the-hood Defect Journal Service initialized.");
    }

    public DefectJournalEntity recordDefect(UUID projectId, String severity, String category,
                                             String sourceComponent, String defectType,
                                             String description, Double metricValue) {
        DefectJournalEntity defect = new DefectJournalEntity(
                projectId, severity, category, sourceComponent, defectType, description, metricValue
        );
        DefectJournalEntity saved = defectJournalRepository.save(defect);
        log.debug("[DEFECT-RECORDED] Under-the-hood defect stored: [{}] {} - {}", category, sourceComponent, description);
        return saved;
    }

    public List<DefectJournalEntity> getDefectsInWindow(UUID projectId, int windowHours) {
        Instant fromTime = Instant.now().minus(windowHours, ChronoUnit.HOURS);
        if (projectId != null) {
            return defectJournalRepository.findByProjectIdAndCreatedAtAfter(projectId, fromTime);
        }
        return defectJournalRepository.findByCreatedAtAfter(fromTime);
    }
}
