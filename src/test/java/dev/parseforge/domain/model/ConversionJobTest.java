package dev.parseforge.domain.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionJobTest {
    private final ConversionRequest request = new ConversionRequest(
            Path.of("input.pdf"),
            Path.of("output"),
            new EngineId("marker"),
            OutputFormat.MARKDOWN);

    @Test
    void followsTheHappyPathStateMachine() {
        ConversionJob job = new ConversionJob(request);

        job.transitionTo(ConversionStatus.PREPARING);
        job.transitionTo(ConversionStatus.RUNNING);
        job.complete(java.util.List.of(Path.of("output/result.md")));

        assertEquals(ConversionStatus.COMPLETED, job.status());
        assertTrue(job.startedAt() != null);
        assertTrue(job.finishedAt() != null);
        assertEquals(1, job.outputFiles().size());
    }

    @Test
    void rejectsInvalidTransitions() {
        ConversionJob job = new ConversionJob(request);

        assertThrows(IllegalStateException.class,
                () -> job.transitionTo(ConversionStatus.COMPLETED));
    }
}
