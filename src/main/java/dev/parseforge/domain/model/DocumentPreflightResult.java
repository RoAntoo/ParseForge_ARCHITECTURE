package dev.parseforge.domain.model;

import java.nio.file.Path;

/** Counts and ratios describe sampled pages, never an exhaustive OCR result. */
public record DocumentPreflightResult(Path file, long fileSize, int pageCount,
        DocumentType type, boolean selectableTextDetected, boolean likelyNeedsOcr,
        double textPageRatio, int sampledPages, long elapsedMillis) {
    public boolean longDocument() { return pageCount >= 200; }
    public boolean sampled() { return sampledPages < pageCount; }
}
