package dev.parseforge.application.port.out;

import dev.parseforge.domain.model.DocumentPreflightResult;
import java.nio.file.Path;

@FunctionalInterface
public interface DocumentPreflightService {
    DocumentPreflightResult inspect(Path file);
}
