package dev.parseforge.application.usecase;

import dev.parseforge.domain.model.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzeDocumentUseCaseTest {
    @Test void cancelledFutureDoesNotHideAnInspectionThatIgnoresInterruption() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        try (var useCase = new AnalyzeDocumentUseCase(file -> {
            if (file.toString().equals("old")) {
                entered.countDown();
                try {
                    while (release.getCount() > 0) try { release.await(); } catch (InterruptedException ignored) { }
                } finally { exited.countDown(); }
            }
            return new DocumentPreflightResult(file, 1, 1, DocumentType.UNKNOWN, false, false, 0, 1, 0);
        })) {
            var old = useCase.analyze(Path.of("old"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(old.cancel(true));
            assertEquals(Path.of("new"), useCase.analyze(Path.of("new")).get(2, TimeUnit.SECONDS).file());
            assertEquals(1, exited.getCount(), "New inspection must finish while the old callable is still running");
            release.countDown(); assertTrue(exited.await(2, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }
    @Test void newSelectionInterruptsOldInspectionAndRunsLatest() throws Exception {
        var entered = new CountDownLatch(1); var interrupted = new CountDownLatch(1);
        try (var useCase = new AnalyzeDocumentUseCase(file -> {
            if (file.toString().equals("old")) {
                entered.countDown();
                try { new CountDownLatch(1).await(); } catch (InterruptedException expected) { interrupted.countDown(); }
            }
            return new DocumentPreflightResult(file, 1, 1, DocumentType.UNKNOWN, false, false, 0, 1, 0);
        })) {
            useCase.analyze(Path.of("old")); assertTrue(entered.await(2, TimeUnit.SECONDS));
            var latest = useCase.analyze(Path.of("new"));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertEquals(Path.of("new"), latest.get(2, TimeUnit.SECONDS).file());
        }
    }
    @Test void timeoutReleasesTheUiAndInterruptsInspection() throws Exception {
        var interrupted = new CountDownLatch(1);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var useCase = new AnalyzeDocumentUseCase(file -> {
            if (file.toString().equals("slow")) {
                entered.countDown();
                while (release.getCount() > 0) try { release.await(); } catch (InterruptedException expected) { interrupted.countDown(); }
            }
            return new DocumentPreflightResult(file, 1, 1, DocumentType.UNKNOWN, false, false, 0, 1, 0);
        })) {
            var future = useCase.analyze(Path.of("slow"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> future.get(10, TimeUnit.SECONDS)).getCause());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertEquals(Path.of("new"), useCase.analyze(Path.of("new")).get(2, TimeUnit.SECONDS).file());
        } finally { release.countDown(); }
    }
}
