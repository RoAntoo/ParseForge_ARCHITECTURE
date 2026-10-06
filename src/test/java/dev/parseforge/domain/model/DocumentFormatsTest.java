package dev.parseforge.domain.model;

import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DocumentFormatsTest {
    @Test void officeAndEpubSelectLightweightEngineAndImagesSelectOcrEngine() {
        for (String extension : new String[]{"DOCX", "epub", "pptx", "xlsx", "xls", "msg", "zip", "html", "txt", "md", "csv", "json", "xml"}) {
            Path file = Path.of("informe ñ." + extension);
            assertTrue(DocumentFormats.supported(file));
            assertTrue(DocumentFormats.supports(new EngineId("markitdown"), file));
            assertFalse(DocumentFormats.supports(new EngineId("marker"), file));
        }
        assertTrue(DocumentFormats.supports(new EngineId("marker"), Path.of("foto.PNG")));
        assertFalse(DocumentFormats.supports(new EngineId("markitdown"), Path.of("foto.PNG")));
        assertTrue(DocumentFormats.isPdf(Path.of("Informe.PDF")));
    }
    @Test void unsupportedInputFailsWithTypedErrorInsteadOfStartingAnEngine() {
        assertFalse(DocumentFormats.supported(Path.of("archivo.exe")));
        assertFalse(DocumentFormats.supported(Path.of("archivo.doc")));
        assertFalse(DocumentFormats.supported(null));
        var request = new ConversionRequest(Path.of("libro.epub"), Path.of("salida"), new EngineId("marker"), OutputFormat.MARKDOWN);
        assertEquals(ErrorCode.FORMAT_UNSUPPORTED, assertThrows(ConversionException.class,
                () -> DocumentFormats.requireSupported(request)).code());
    }
}
