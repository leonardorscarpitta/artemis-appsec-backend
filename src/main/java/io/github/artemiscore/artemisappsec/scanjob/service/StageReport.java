package io.github.artemiscore.artemisappsec.scanjob.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.finding.service.DetectedIssue;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageOutcome;

public record StageReport(StageOutcome status, String tool, int durationSeconds, String log, List<DetectedIssue> findings) {
}
