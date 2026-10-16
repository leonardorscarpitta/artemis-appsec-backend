package io.github.artemiscore.artemis.pipelinerun.persistence;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import io.github.artemiscore.artemis.pipelinerun.domain.PipelineRun;
import io.github.artemiscore.artemis.pipelinerun.domain.RunStatus;

public final class PipelineRunSpecifications {

	private PipelineRunSpecifications() {
	}

	public static Specification<PipelineRun> hasProject(UUID projectId) {
		return (root, query, cb) -> projectId == null ? null : cb.equal(root.get("projectId"), projectId);
	}

	public static Specification<PipelineRun> hasStatus(RunStatus status) {
		return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
	}

	public static Specification<PipelineRun> branchContains(String branch) {
		return (root, query, cb) -> branch == null || branch.isBlank() ? null
				: cb.like(cb.lower(root.get("branch")), "%" + branch.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%", '\\');
	}
}
