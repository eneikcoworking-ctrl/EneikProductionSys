package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskGateLogEntity;
import com.eneik.production.repositories.TaskGateLogRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class GateOrchestrator {

    private final List<GateCheck> gateChecks;
    private final TaskRepository taskRepository;
    private final TaskGateLogRepository taskGateLogRepository;
    private final ObjectMapper objectMapper;
    private final ClientDeliverableReadinessService readinessService;

    public GateOrchestrator(List<GateCheck> gateChecks, TaskRepository taskRepository, TaskGateLogRepository taskGateLogRepository,
            ObjectMapper objectMapper, ClientDeliverableReadinessService readinessService) {
        this.gateChecks = gateChecks;
        this.taskRepository = taskRepository;
        this.taskGateLogRepository = taskGateLogRepository;
        this.objectMapper = objectMapper;
        this.readinessService = readinessService;
    }

    @Transactional
    public void runTaskSpecGate(TaskEntity task) {
        runQualityGate(task, Set.of(GateStage.TASK_SPEC));
    }

    @Transactional
    public void runQualityGate(TaskEntity task) {
        runQualityGate(task, EnumSet.allOf(GateStage.class));
    }

    private void runQualityGate(TaskEntity task, Set<GateStage> stages) {
        boolean buildPhase = task.getProject() != null
                && readinessService.isBuildPhase(task.getProject().getId());

        List<GateCheck> applicable = gateChecks.stream()
                .filter(check -> stages.contains(check.stage()))
                .filter(check -> check.supports(task))
                .filter(check -> !(buildPhase && check.isBuildPhaseExempt()))
                .toList();
        List<GateResult> results = applicable.stream().map(check -> check.check(task)).toList();

        // 2026-08-22 (ACP-105): `allMatch` on an empty list returns true. A task whose role no gate
        // supports produces an empty `results` here - eight of the thirteen roles have no gate at all -
        // and it is then recorded as having passed every applicable check when none was applied. That is
        // the same structure §1 rejects for falsifying a product that does not run: quantification over an
        // empty domain, trivially satisfied and informative about nothing.
        //
        // `allMatch` is a fraction in disguise. It says "N of N passed", and at N=0 it reports 0/0 as
        // success. Invariant 8 - state the denominator - was applied to ratios and never to booleans,
        // because a boolean does not look like it has one.
        //
        // The boolean is deliberately NOT changed. Making it false when nothing applied would fail every
        // task of eight roles and stop the factory - a fix that damages rather than strengthens. What is
        // added is the denominator, so a reader that needs real evidence can ask for it; see
        // TaskEntity.isVerifiedForDelivery, which now requires both the stage and a non-zero count.
        boolean allPassed = results.stream().allMatch(GateResult::passed);
        // Per STAGE, not in total. `stages` below records the stages REQUESTED, and runQualityGate always
        // requests all of them - so "IMPLEMENTATION_RESULT is in stages" says only that it was asked for,
        // never that any check for it applied. Counting all applicable checks together repeats the very
        // substitution this change exists to remove: a task with a TASK_SPEC check and no delivery check
        // would show a positive count beside a requested delivery stage, and read as verified for
        // delivery. The count has to be per stage or it answers the wrong question.
        java.util.Map<GateStage, Long> appliedByStage = applicable.stream()
                .collect(java.util.stream.Collectors.groupingBy(GateCheck::stage,
                        java.util.stream.Collectors.counting()));

        ObjectNode report = objectMapper.createObjectNode();
        report.put("passed", allPassed);
        // 2026-08-18: a verdict must carry its own subject. This method has two public entry points -
        // runTaskSpecGate(task), called by TechnicalLeadCompiler the moment a task is CREATED, and
        // runQualityGate(task), called by ClaimService when an implementer FINISHES - and both write the
        // same boolean into the same field. Nothing in the field or in any of its readers says which
        // question was answered, so "this task is well specified" and "this task's work passed every
        // applicable check" are indistinguishable after the fact.
        //
        // Both writes are true statements. That is what makes the substitution invisible: it cannot be
        // caught by asking whether the value is correct, only by asking what it is about. Measured on
        // test-forty-ninth: task f163e834 is status done with qualityGatePassed true, no claim, no Jules
        // session, no PR - its flag was written by the spec gate two seconds after creation and never
        // revisited, and the gap between created_at and the gate log is 2-5 seconds for EVERY task in
        // the project, which is that same creation-time gating.
        //
        // Recording the stages makes the subject part of the verdict. No reader changes behaviour and
        // the boolean is untouched; what changes is that "verified" can now be asked of a specific
        // question. A task whose only gate log carries stages [TASK_SPEC] has never been verified for
        // delivery, and that is now a fact anything can read rather than an inference from timestamps.
        ObjectNode appliedNode = report.putObject("applicableChecksByStage");
        for (GateStage stage : GateStage.values()) {
            appliedNode.put(stage.name(), appliedByStage.getOrDefault(stage, 0L));
        }
        ArrayNode stageNames = report.putArray("stages");
        stages.stream().map(Enum::name).sorted().forEach(stageNames::add);

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

        TaskGateLogEntity logEntity = new TaskGateLogEntity();
        logEntity.setTask(task);
        logEntity.setPassed(allPassed);
        logEntity.setReport(report);
        logEntity.setCreatedAt(Instant.now());
        taskGateLogRepository.save(logEntity);
    }
}
