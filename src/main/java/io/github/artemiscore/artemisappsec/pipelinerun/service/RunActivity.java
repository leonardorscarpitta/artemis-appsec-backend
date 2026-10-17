package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.time.LocalDate;

/** Pipeline runs started on one day, by final status (running and queued runs are not counted). */
public record RunActivity(LocalDate date, long succeeded, long failed, long cancelled) {
}
