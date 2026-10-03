package dev.parseforge.infrastructure.document;

import dev.parseforge.domain.exception.*;
import dev.parseforge.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class PdfBoxDocumentPreflightTest {
    @TempDir Path temp;
    private final PdfBoxDocumentPreflight service = new PdfBoxDocumentPreflight();
    @Test void digitalUnicodeAndSpaces() throws Exception {
        Path path = PdfFixtures.create(temp.resolve("Carpeta ñ 文/documento digital.pdf"), "text", "text");
        var result = service.inspect(path);
        assertEquals(DocumentType.DIGITAL, result.type()); assertTrue(result.selectableTextDetected());
        assertFalse(result.likelyNeedsOcr()); assertEquals(1, result.textPageRatio());
        assertEquals(2, result.pageCount()); assertEquals(Files.size(path), result.fileSize());
    }
    @Test void imageOnlyIsLikelyScanned() throws Exception {
        var result = service.inspect(PdfFixtures.create(temp.resolve("scan.pdf"), "image", "image"));
        assertEquals(DocumentType.SCANNED, result.type()); assertFalse(result.selectableTextDetected()); assertTrue(result.likelyNeedsOcr());
    }
    @Test void mixed() throws Exception {
        var result = service.inspect(PdfFixtures.create(temp.resolve("mixed.pdf"), "text", "image"));
        assertEquals(DocumentType.MIXED, result.type()); assertEquals(.5, result.textPageRatio()); assertTrue(result.likelyNeedsOcr());
    }
    @Test void blankAndSparseAreUnknownRatherThanScanned() throws Exception {
        for (String kind : new String[]{"blank", "sparse"}) {
            var result = service.inspect(PdfFixtures.create(temp.resolve(kind + ".pdf"), kind));
            assertEquals(DocumentType.UNKNOWN, result.type()); assertFalse(result.likelyNeedsOcr());
        }
    }
    @Test void zeroPages() throws Exception {
        assertEquals(DocumentType.UNKNOWN, service.inspect(PdfFixtures.create(temp.resolve("empty.pdf"))).type());
    }
    @Test void corruptAndMissingHaveDistinctCodes() throws Exception {
        Path corrupt = Files.writeString(temp.resolve("corrupt.pdf"), "%PDF-1.7\nnot a PDF");
        assertEquals(ErrorCode.PDF_INVALID, assertThrows(ConversionException.class, () -> service.inspect(corrupt)).code());
        assertEquals(ErrorCode.FILE_INACCESSIBLE, assertThrows(ConversionException.class, () -> service.inspect(temp.resolve("missing.pdf"))).code());
    }
    @Test void protectedDocumentDoesNotClaimCorruption() throws Exception {
        Path file = temp.resolve("protected.pdf");
        try (var doc = new PDDocument()) {
            doc.addPage(new PDPage()); doc.protect(new StandardProtectionPolicy("owner", "secret", new AccessPermission())); doc.save(file.toFile());
        }
        assertThrows(IllegalStateException.class, () -> service.inspect(file));
    }
    @Test void representativeSamplingIncludesEndsAndDoesNotReadOnlyTheCover() throws Exception {
        String[] pages = new String[240]; Arrays.fill(pages, "image"); pages[0] = "text";
        var result = service.inspect(PdfFixtures.create(temp.resolve("long.pdf"), pages));
        assertEquals(240, result.pageCount()); assertEquals(20, result.sampledPages()); assertTrue(result.longDocument());
        assertEquals(DocumentType.SCANNED, result.type()); assertEquals(.05, result.textPageRatio());
        int[] sample = PdfBoxDocumentPreflight.samplePages(240);
        assertEquals(0, sample[0]); assertEquals(239, sample[19]); assertEquals(20, Arrays.stream(sample).distinct().count());
    }
    @Test void twentyPagesAreExhaustive() throws Exception {
        String[] pages = new String[20]; Arrays.fill(pages, "text");
        var result = service.inspect(PdfFixtures.create(temp.resolve("small.pdf"), pages));
        assertFalse(result.sampled()); assertEquals(DocumentType.DIGITAL, result.type());
    }
    @Test void replacementAtSamePathIsInspectedAgain() throws Exception {
        Path file = PdfFixtures.create(temp.resolve("changed.pdf"), "text");
        assertEquals(DocumentType.DIGITAL, service.inspect(file).type());
        PdfFixtures.create(file, "image"); assertEquals(DocumentType.SCANNED, service.inspect(file).type());
    }
}
