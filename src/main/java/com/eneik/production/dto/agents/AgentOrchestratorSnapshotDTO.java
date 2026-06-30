package com.eneik.production.dto.agents;

import java.util.List;
import java.util.Map;

public class AgentOrchestratorSnapshotDTO {
    private final List<AgentAccountDTO> accounts;
    private final List<AgentTaskDTO> tasks;
    private final Map<String, Long> summary;

    public AgentOrchestratorSnapshotDTO(List<AgentAccountDTO> accounts,
                                        List<AgentTaskDTO> tasks,
                                        Map<String, Long> summary) {
        this.accounts = accounts;
        this.tasks = tasks;
        this.summary = summary;
    }

    public List<AgentAccountDTO> getAccounts() {
        return accounts;
    }

    public List<AgentTaskDTO> getTasks() {
        return tasks;
    }

    public Map<String, Long> getSummary() {
        return summary;
    }
}
