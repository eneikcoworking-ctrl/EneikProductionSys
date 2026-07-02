package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GateOrchestrator {

    private final List<GateCheck> gateChecks;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public GateOrchestrator(List<GateCheck> gateChecks, TaskRepository taskRepository, ObjectMapper objectMapper) {
        this.gateChecks = gateChecks;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void runQualityGate(TaskEntity task) {
        List<GateResult> results = gateChecks.stream()
                .map(check -> check.check(task))
                .toList();

        boolean allPassed = results.stream().allMatch(GateResult::passed);

        ObjectNode report = objectMapper.createObjectNode();
        report.put("passed", allPassed);

        ArrayNode checks = report.putArray("checks");
        for (GateResult res : results) {
            ObjectNode checkNode = checks.addObject();
            checkNode.put("name", res.checkName());
            checkNode.put("passed", res.passed());
            ArrayNode failReasons = checkNode.putArray("failureReasons");
            res.failureReasons().forEach(failReasons::add);
        }

        task.setQualityGatePassed(allPassed);
        task.setQualityGateReport(report);
        taskRepository.save(task);
    }
}
