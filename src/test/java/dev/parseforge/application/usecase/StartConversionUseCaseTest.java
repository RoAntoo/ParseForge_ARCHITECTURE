package dev.parseforge.application.usecase;

import dev.parseforge.application.port.out.ConversionEngine;
import dev.parseforge.application.port.out.ConversionEventListener;
import dev.parseforge.application.port.out.EngineRegistry;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.ConversionResult;
import dev.parseforge.domain.model.ConversionStatus;
import dev.parseforge.domain.model.EngineDescriptor;
import dev.parseforge.domain.model.EngineId;
import dev.parseforge.domain.model.EngineState;
import dev.parseforge.domain.model.OutputFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartConversionUseCaseTest {
    private static final EngineId ENGINE_ID = new EngineId("marker");

    @Test
    void executesTheSelectedEngineAndReturnsItsResult() {
        ConversionResult expected = new ConversionResult(
                ConversionStatus.COMPLETED, 0, List.of(Path.of("result.md")), null,
                Duration.ofSeconds(1));
        FakeEngine engine = new FakeEngine(expected);
        EngineRegistry registry = ignored -> engine;
        StartConversionUseCase useCase = new StartConversionUseCase(registry, Runnable::run);

        ConversionResult actual = useCase.start(request(), ignored -> { }).join();

        assertEquals(expected, actual);
        assertEquals(1, engine.conversions);
        assertFalse(useCase.hasActiveConversion());
    }

    @Test
    void cancellationIsDelegatedToTheActiveEngine() throws Exception {
        CountDownLatch conversionStarted = new CountDownLatch(1);
        CountDownLatch releaseConversion = new CountDownLatch(1);
        BlockingEngine engine = new BlockingEngine(conversionStarted, releaseConversion);
        try (var worker = Executors.newSingleThreadExecutor()) {
            StartConversionUseCase useCase = new StartConversionUseCase(ignored -> engine, worker);
            CompletableFuture<ConversionResult> result = useCase.start(request(), ignored -> { });

            assertTrue(conversionStarted.await(2, TimeUnit.SECONDS));
            assertTrue(useCase.cancelActive());
            releaseConversion.countDown();

            assertEquals(ConversionStatus.CANCELLED, result.get(2, TimeUnit.SECONDS).status());
            assertTrue(engine.cancelled);
            assertFalse(useCase.hasActiveConversion());
        }
    }

    private ConversionRequest request() {
        return new ConversionRequest(
                Path.of("input.pdf"), Path.of("output"), ENGINE_ID, OutputFormat.MARKDOWN);
    }

    private static final class FakeEngine implements ConversionEngine {
        private final ConversionResult result;
        private int conversions;

        private FakeEngine(ConversionResult result) {
            this.result = result;
        }

        @Override
        public EngineDescriptor descriptor() {
            return new EngineDescriptor(ENGINE_ID, "Marker", "test");
        }

        @Override
        public EngineState state() {
            return EngineState.AVAILABLE;
        }

        @Override
        public ConversionResult convert(ConversionRequest request, ConversionEventListener listener) {
            conversions++;
            return result;
        }

        @Override
        public void cancel() {
        }
    }

    private static final class BlockingEngine implements ConversionEngine {
        private final CountDownLatch started;
        private final CountDownLatch release;
        private volatile boolean cancelled;

        private BlockingEngine(CountDownLatch started, CountDownLatch release) {
            this.started = started;
            this.release = release;
        }

        @Override
        public EngineDescriptor descriptor() {
            return new EngineDescriptor(ENGINE_ID, "Marker", "test");
        }

        @Override
        public EngineState state() {
            return EngineState.AVAILABLE;
        }

        @Override
        public ConversionResult convert(ConversionRequest request, ConversionEventListener listener) {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return new ConversionResult(
                    cancelled ? ConversionStatus.CANCELLED : ConversionStatus.COMPLETED,
                    cancelled ? -1 : 0,
                    List.of(),
                    cancelled ? "cancelled" : null,
                    Duration.ZERO);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
