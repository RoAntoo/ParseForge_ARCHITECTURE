package dev.parseforge.application.port.out;
import dev.parseforge.domain.model.EngineInstallProgress;
@FunctionalInterface public interface EngineProgressListener { void onProgress(EngineInstallProgress progress); }

