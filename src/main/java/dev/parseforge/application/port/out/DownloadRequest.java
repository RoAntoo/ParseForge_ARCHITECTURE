package dev.parseforge.application.port.out;
import java.net.URI;
import java.nio.file.Path;
public record DownloadRequest(URI uri, Path destination, String sha256, long expectedBytes) { }

