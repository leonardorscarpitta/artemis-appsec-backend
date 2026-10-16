package io.github.artemiscore.artemisappsec.project.service;

import java.util.List;

public record ProjectDetails(ProjectSummary project, ProjectSettings settings, List<PipelineStageView> pipeline) {
}
