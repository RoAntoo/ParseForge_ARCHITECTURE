package dev.parseforge.infrastructure.engine.markitdown;

import dev.parseforge.application.port.out.*;
import dev.parseforge.infrastructure.engine.*;

/** Uses the common pinned, staged installer with this engine's private runtime. */
public final class MarkItDownInstaller extends PinnedPythonEngineInstaller {
    public MarkItDownInstaller(EnginePathResolver paths, EngineManifestRepository manifests, DownloadClient downloads,
                           EngineVerifier verifier, ProcessExecutor executor) {
        super(paths, manifests, downloads, verifier, executor, ManagedMarkItDownRuntime::new);
    }
}
