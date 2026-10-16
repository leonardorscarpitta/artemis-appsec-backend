package io.github.artemiscore.artemis.project.service;

import java.util.List;

public record ProjectDetails(ProjectSummary project, ProjectSettings settings, List<PipelineStageView> pipeline) {
}
