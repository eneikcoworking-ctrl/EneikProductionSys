package com.eneik.production.services.task;

import com.eneik.production.dto.DecompositionResponseDto;
import com.eneik.production.dto.TaskShortDto;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DecompositionService {
    private static final Logger log = LoggerFactory.getLogger(DecompositionService.class);

    private final TaskRepository taskRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    private static final List<DecompositionRule> RULES = List.of(
            new DecompositionRule(List.of("ui", "форм", "экран", "frontend"), List.of("BARCAN-TAG-03", "BARCAN-TAG-11")),
            new DecompositionRule(List.of("данны", "база", "таблиц", "schema"), List.of("BARCAN-TAG-08")),
            new DecompositionRule(List.of("api", "backend", "endpoint"), List.of("BARCAN-TAG-02")),
            new DecompositionRule(List.of("auth", "парол", "логин", "безопасн"), List.of("BARCAN-TAG-07"))
    );

    private static final List<String> ALWAYS_REVIEW_TAGS = List.of("BARCAN-TAG-00", "BARCAN-TAG-01");

    public DecompositionService(TaskRepository taskRepository, RoleRepository roleRepository, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.roleRepository = roleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DecompositionResponseDto decompose(String requirementText) {
        Set<String> matchedTags = new LinkedHashSet<>();
        boolean ruleMatched = false;
        String lowerText = requirementText.toLowerCase();

        for (DecompositionRule rule : RULES) {
            if (rule.keywords().stream().anyMatch(lowerText::contains)) {
                matchedTags.addAll(rule.tags());
                ruleMatched = true;
            }
        }

        if (!ruleMatched) {
            log.warn("не удалось классифицировать, нужна ручная разметка: {}", requirementText);
        }

        matchedTags.addAll(ALWAYS_REVIEW_TAGS);

        UUID sourceRequirementId = UUID.randomUUID();
        List<TaskEntity> tasksToSave = matchedTags.stream()
                .map(tag -> {
                    TaskEntity task = new TaskEntity();
                    RoleEntity role = roleRepository.findById(tag)
                            .orElseThrow(() -> new RuntimeException("Role not found: " + tag));
                    task.setRole(role);
                    task.setTitle(TaskTitleBuilder.build(tag, requirementText));
                    task.setDescription("[" + tag + "] " + requirementText);
                    task.setStatus(TaskStatus.queued);

                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.put("requirementText", requirementText);
                    payload.put("sourceRequirementId", sourceRequirementId.toString());
                    task.setPayload(payload);

                    return task;
                })
                .collect(Collectors.toList());

        List<TaskEntity> savedTasks = taskRepository.saveAll(tasksToSave);

        List<TaskShortDto> taskDtos = savedTasks.stream()
                .map(t -> new TaskShortDto(t.getId(), t.getRole().getTag(), TaskTitleBuilder.displayTitle(t), t.getDescription()))
                .collect(Collectors.toList());

        return new DecompositionResponseDto(sourceRequirementId, taskDtos);
    }

    private record DecompositionRule(List<String> keywords, List<String> tags) {}
}
