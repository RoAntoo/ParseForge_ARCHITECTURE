package dev.parseforge.application.settings;
import dev.parseforge.domain.model.*;
import dev.parseforge.domain.exception.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentAdviceTest {
    private DocumentAdvice advice(DocumentType type, String engine, boolean ready) {
        return DocumentAdvice.forDocument(new DocumentPreflightResult(Path.of("test.pdf"), 0, 1, type, false, false, 0, 1, 0), new EngineId(engine), ready);
    }
    @Test void digitalRecommendsReadyLightweightEngine() {
        assertEquals("markitdown", advice(DocumentType.DIGITAL, "marker", true).suggestedEngine());
        assertEquals("", advice(DocumentType.DIGITAL, "marker", false).suggestedEngine());
        assertFalse(advice(DocumentType.DIGITAL, "marker", true).incompatible());
    }
    @Test void scanNeedsOcrAndBlocksLightweight() {
        assertEquals("marker", advice(DocumentType.SCANNED, "markitdown", true).suggestedEngine());
        assertTrue(advice(DocumentType.SCANNED, "markitdown", true).incompatible());
        assertFalse(advice(DocumentType.SCANNED, "marker", true).incompatible());
    }
    @Test void mixedWarnsAboutPartialOutput() {
        assertEquals("marker", advice(DocumentType.MIXED, "markitdown", true).suggestedEngine());
        assertTrue(advice(DocumentType.MIXED, "markitdown", true).message().contains("omitir"));
    }
    @Test void unknownIsNeutral() {
        assertEquals("", advice(DocumentType.UNKNOWN, "marker", true).suggestedEngine());
        assertFalse(advice(DocumentType.UNKNOWN, "markitdown", true).incompatible());
    }
    @Test void errorsDoNotExposeTechnicalMessages() {
        for (ErrorCode code : ErrorCode.values()) {
            String message = ConversionMessages.forError(new ConversionException(code, "Traceback internal secret"));
            assertFalse(message.contains("Traceback")); assertFalse(message.isBlank());
        }
        assertTrue(ConversionMessages.forCode(ErrorCode.PDF_INVALID).contains("dañado"));
        assertTrue(ConversionMessages.forCode(ErrorCode.DISK_SPACE_LOW).contains("espacio"));
        assertEquals("Conversión cancelada", ConversionMessages.forCode(ErrorCode.USER_CANCELLED));
    }
}
