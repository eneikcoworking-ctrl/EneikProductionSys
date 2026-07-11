package com.eneik.production.dto;

import com.eneik.production.dto.dashboard.AgentDashboardDto;
import com.eneik.production.dto.dashboard.PipelineDashboardDto;
import com.eneik.production.dto.dashboard.QueueDashboardDto;
import java.util.List;

public record ProjectDashboardDto(
        ProjectDto project,
        int agentCount,
        long openWishlistCount,
        QueueDashboardDto queue,
        PipelineDashboardDto pipeline,
        List<AgentDashboardDto> agents,
        List<WishlistResponseDto> wishlist,
        List<TaskDto> tasks
) {
}
