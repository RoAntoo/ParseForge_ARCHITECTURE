package dev.parseforge.application.settings;

import dev.parseforge.domain.model.EngineId;

/** Presentation facts about scope/resources, never a quality score. */
public record EngineProfile(EngineId id, String name, String description, String level,
                            int scopeBars, long installedBytes, boolean ocr) {
    public static EngineProfile marker() {
        return new EngineProfile(new EngineId("marker"), "Marker",
                "Conversión avanzada con reconocimiento de estructura y OCR. Ideal para documentos complejos.",
                "Avanzado", 5, 3_230_000_000L, true);
    }
    public static EngineProfile markItDown(long measuredBytes) {
        return new EngineProfile(new EngineId("markitdown"), "MarkItDown",
                "Motor ligero para PDFs digitales con texto seleccionable. Ideal cuando no necesitás OCR ni análisis avanzado.",
                "Ligero", 2, measuredBytes, false);
    }
}
