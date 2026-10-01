package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.domain.exception.EngineInstallException;
import dev.parseforge.infrastructure.engine.marker.MarkerProgressParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManagedEngineManagerTest {
    @TempDir Path temp;
    private final EngineId id = new EngineId("marker");
    private EnginePathResolver paths;
    private EngineManifestRepository manifests;
    private EngineInstaller installer;
    private EngineVerifier verifier;
    private ManagedEngineManager manager;
    @BeforeEach void setup() throws Exception {
        paths = new EnginePathResolver(Map.of(), temp.toString(), temp.toString());
        manifests = new EngineManifestRepository();
        installer = mock(EngineInstaller.class); verifier = mock(EngineVerifier.class);
        when(verifier.verify(any(), any(), anyBoolean(), anyBoolean(), any(), any())).thenReturn(new EngineVerificationResult(true, "ok"));
        manager = new ManagedEngineManager(paths, manifests, installer, verifier);
    }
    @Test void stateIsRecoveredAcrossManagerInstancesAndBrokenIsDetected() throws Exception {
        assertEquals(EngineState.NOT_INSTALLED, manager.check(id));
        Files.createDirectories(paths.engine(id));
        assertEquals(EngineState.READY, manager.check(id));
        var restarted = new ManagedEngineManager(paths, manifests, installer, verifier);
        assertEquals(EngineState.READY, restarted.check(id));
        when(verifier.verify(any(), any(), anyBoolean(), anyBoolean(), any(), any())).thenReturn(new EngineVerificationResult(false, "missing"));
        assertEquals(EngineState.BROKEN, restarted.check(id));
    }
    @Test void failedRepairPreservesPreviousReadyInstallation() throws Exception {
        Files.createDirectories(paths.engine(id)); Files.writeString(paths.engine(id).resolve("original"), "previous");
        doThrow(new EngineInstallException(EngineInstallException.Code.DOWNLOAD_FAILED, "offline"))
                .when(installer).repair(any(), any(), any(), any());
        assertThrows(CompletionException.class, () -> new RepairEngineUseCase(manager, Runnable::run).execute(id, p -> {}).join());
        assertEquals(EngineState.READY, manager.getState(id));
        assertEquals("previous", Files.readString(paths.engine(id).resolve("original")));
    }
    @Test void failureRetainsReadyAndReportsFailedEvent() throws Exception {
        Files.createDirectories(paths.engine(id));
        doThrow(new EngineInstallException(EngineInstallException.Code.CHECKSUM_MISMATCH, "bad hash"))
                .when(installer).install(any(), any(), any(), any());
        List<EngineInstallProgress> events = new ArrayList<>();
        assertThrows(EngineInstallException.class, () -> manager.install(id, events::add));
        assertEquals(EngineState.READY, manager.getState(id));
        assertEquals(EngineInstallProgress.Phase.FAILED, events.getLast().phase());
    }
    @Test void cancellationIsDeliveredToInstallerAndStateResets() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(invocation -> {
            var cancellation = (OperationCancellation) invocation.getArgument(3);
            started.countDown(); release.await(5, TimeUnit.SECONDS); cancellation.check(); return null;
        }).when(installer).install(any(), any(), any(), any());
        try (var worker = Executors.newSingleThreadExecutor()) {
            var pending = new InstallEngineUseCase(manager, worker).execute(id, p -> {});
            assertTrue(started.await(5, TimeUnit.SECONDS));
            new CancelEngineOperationUseCase(manager).execute(id); release.countDown();
            var error = assertThrows(CompletionException.class, pending::join);
            assertEquals(EngineInstallException.Code.INSTALL_CANCELLED, ((EngineInstallException)error.getCause()).code());
            assertEquals(EngineState.NOT_INSTALLED, manager.getState(id));
        }
    }
    @Test void activeOperationExcludesUninstallAndSecondInstall() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(i -> { started.countDown(); release.await(5, TimeUnit.SECONDS); return null; }).when(installer).install(any(), any(), any(), any());
        try (var worker = Executors.newSingleThreadExecutor()) {
            var future = new InstallEngineUseCase(manager, worker).execute(id, p -> {});
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> manager.uninstall(id, p -> {}));
            assertThrows(IllegalStateException.class, () -> manager.install(id, p -> {}));
            release.countDown(); future.join();
        }
    }
    @Test void useCasesDispatchRepairUninstallAndCheck() throws Exception {
        new RepairEngineUseCase(manager, Runnable::run).execute(id, p -> {}).join();
        new UninstallEngineUseCase(manager, Runnable::run).execute(id, p -> {}).join();
        assertEquals(EngineState.NOT_INSTALLED, new CheckEngineStatusUseCase(manager, Runnable::run).execute(id).join());
        verify(installer).repair(any(), any(), any(), any()); verify(installer).uninstall(any(), any(), any());
    }
    @Test void useCasesPassRuntimeProbeChoiceThroughManagerForInstallAndRepair() {
        new InstallEngineUseCase(manager, Runnable::run).execute(id, EngineInstallOptions.WITH_HEALTH_CHECK, p -> {}).join();
        new RepairEngineUseCase(manager, Runnable::run).execute(id, EngineInstallOptions.DEFAULT, p -> {}).join();
        verify(installer).install(any(), eq(EngineInstallOptions.WITH_HEALTH_CHECK), any(), any());
        verify(installer).repair(any(), eq(EngineInstallOptions.DEFAULT), any(), any());
    }
    @Test void interruptedSwapRestoresBackupAndCleansAbandonedStaging() throws Exception {
        Path engine = paths.engine(id); Files.createDirectories(engine.getParent());
        Files.createDirectory(engine.resolveSibling("marker.previous"));
        Files.createDirectory(engine.resolveSibling("marker.installing-abandoned"));
        assertEquals(EngineState.READY, manager.check(id));
        assertTrue(Files.exists(engine));
        assertFalse(Files.exists(engine.resolveSibling("marker.previous")));
        assertFalse(Files.exists(engine.resolveSibling("marker.installing-abandoned")));
    }
    @Test void markerStagePercentagesDoNotPretendToBeOverallConversionProgress() {
        assertTrue(new MarkerProgressParser().parse("Recognizing Text: 100%| 10/10").isEmpty());
        assertEquals(-1, EngineInstallProgress.phase(EngineInstallProgress.Phase.PREPARING, "preparing").fraction());
        assertEquals(0.5, new EngineInstallProgress(EngineInstallProgress.Phase.DOWNLOADING, "file", 50, 100).fraction());
    }
    @Test void anotherManagerCannotRecoverStagingOrRemoveLeasedRuntime() throws Exception {
        Path root = paths.engine(id); Files.createDirectories(root.resolve("runtime/python"));
        Files.createDirectories(root.resolve("runtime/llamacpp")); Files.createFile(root.resolve("runtime/python/python.exe"));
        Files.createFile(root.resolve("runtime/llamacpp/llama-server.exe"));
        Path input = Files.writeString(temp.resolve("input.pdf"), "fixture");
        Path output = Files.createDirectory(temp.resolve("output"));
        var other = new ManagedEngineManager(paths, manifests, installer, verifier);
        try (var lease = manager.acquire(new ConversionRequest(input, output, id, OutputFormat.MARKDOWN))) {
            assertFalse(lease.command().inheritEnvironment());
            assertThrows(IllegalStateException.class, () -> other.uninstall(id, p -> {}));
            verifyNoInteractions(installer);
        }
        other.uninstall(id, p -> {});
        verify(installer).uninstall(any(), any(), any());
    }
}
