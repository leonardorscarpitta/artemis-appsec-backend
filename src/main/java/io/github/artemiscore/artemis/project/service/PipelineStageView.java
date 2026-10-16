package io.github.artemiscore.artemis.project.service;

import java.util.List;

import io.github.artemiscore.artemis.shared.domain.FailThreshold;
import io.github.artemiscore.artemis.shared.domain.Phase;
import io.github.artemiscore.artemis.shared.domain.Stage;

/** {@code failOn} is null for artifact-only stages. */
public record PipelineStageView(Stage id, Phase phase, List<String> tools, String tool, boolean enabled,
		FailThreshold failOn, int minutes) {

	public static PipelineStageView defaults(Stage stage) {
		return new PipelineStageView(stage, stage.phase(), stage.tools(), stage.defaultTool(), stage.enabledByDefault(),
				stage.defaultFailOn(), stage.estimatedMinutes());
	}
}
