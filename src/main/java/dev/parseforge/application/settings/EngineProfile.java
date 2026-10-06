package dev.parseforge.application.settings;

import dev.parseforge.domain.model.EngineId;

/** Presentation facts about scope/resources, never a quality score. */
public record EngineProfile(EngineId id, String name, String description, String level,
                            int scopeBars, long installedBytes, boolean ocr) {
    public static EngineProfile marker() {
        return new EngineProfile(new EngineId("marker"), "Marker",
                "PDF e imágenes con reconocimiento de estructura y OCR.",
                "Avanzado", 5, 3_230_000_000L, true);
    }
    public static EngineProfile markItDown(long measuredBytes) {
        return new EngineProfile(new EngineId("markitdown"), "MarkItDown",
                "PDF digital, Word, EPUB, PowerPoint, Excel, HTML y texto. Sin OCR.",
                "Ligero", 2, measuredBytes, false);
    }
}
