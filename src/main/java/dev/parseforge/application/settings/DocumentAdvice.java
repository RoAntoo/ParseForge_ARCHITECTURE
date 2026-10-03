package dev.parseforge.application.settings;

import dev.parseforge.domain.model.*;

public record DocumentAdvice(String message, String suggestedEngine, boolean incompatible) {
    public static DocumentAdvice forDocument(DocumentPreflightResult result, EngineId selected, boolean markItDownReady) {
        boolean lightweight = selected != null && selected.value().equals("markitdown");
        return switch (result.type()) {
            case DIGITAL -> new DocumentAdvice("Texto seleccionable detectado. Marker y MarkItDown son compatibles."
                    + (markItDownReady ? " Sugerido para velocidad: MarkItDown." : ""), markItDownReady ? "markitdown" : "", false);
            case SCANNED -> new DocumentAdvice("Parece un PDF escaneado. OCR probablemente necesario. Marker puede procesarlo mediante OCR. "
                    + (lightweight ? "MarkItDown no incluye OCR; elegí Marker para este documento."
                    : "Los documentos escaneados pueden tardar considerablemente más."), "marker", lightweight);
            case MIXED -> new DocumentAdvice("Parece combinar páginas digitales y escaneadas. Se recomienda Marker para reconocer también las imágenes. "
                    + (lightweight ? "MarkItDown puede omitir el texto de las páginas escaneadas." : ""), "marker", false);
            case UNKNOWN -> new DocumentAdvice("No se pudo determinar con seguridad el tipo de documento. Podés convertirlo con el motor seleccionado.", "", false);
        };
    }
}
