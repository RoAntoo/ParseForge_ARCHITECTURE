package dev.parseforge.application.port.out;
import java.nio.file.Path;
public record DownloadResult(Path file, long bytes) { }

