package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.util.List;

/** Public contract of the pipeline run service. */
public interface PipelineRunService {

	List<RunView> find(RunFilter filter);

	RunsSummary summary();

	RunDetails get(long number);

	/** Queues a new manual run of the same commit using the repository's current pipeline configuration. */
	RunDetails rerun(long number, String actor);

	RunDetails cancel(long number);
}
