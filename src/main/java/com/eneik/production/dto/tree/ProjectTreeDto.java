package com.eneik.production.dto.tree;

import java.util.List;
import java.util.UUID;

public record ProjectTreeDto(
        UUID projectId,
        List<FeatureBranchDto> branches,
        List<SeedDto> seeds,
        List<AnnotationDto> trunkAnnotations
) {
}
