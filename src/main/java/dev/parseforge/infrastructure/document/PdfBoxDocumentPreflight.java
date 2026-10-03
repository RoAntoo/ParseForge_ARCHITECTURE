package dev.parseforge.infrastructure.document;

import dev.parseforge.application.port.out.DocumentPreflightService;
import dev.parseforge.domain.exception.*;
import dev.parseforge.domain.model.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** File-backed, local inspection. No rendering, image decoding, OCR or persisted text. */
public final class PdfBoxDocumentPreflight implements DocumentPreflightService {
    public static final int MAX_SAMPLE = 20, MIN_CHARACTERS = 40;
    public DocumentPreflightResult inspect(Path file) {
        long started = System.nanoTime();
        if (!Files.isRegularFile(file) || !Files.isReadable(file))
            throw new ConversionException(ErrorCode.FILE_INACCESSIBLE, "PDF inaccessible: " + file);
        try (var document = open(file)) {
            int total = document.getNumberOfPages();
            int[] pages = samplePages(total);
            int textPages = 0, imageOnlyPages = 0;
            boolean anyText = false;
            for (int index : pages) {
                checkInterrupted();
                var stripper = new BoundedTextProbe();
                stripper.setStartPage(index + 1); stripper.setEndPage(index + 1);
                try { stripper.getText(document); } catch (EnoughText ignored) { }
                anyText |= stripper.characters > 0;
                if (stripper.characters >= MIN_CHARACTERS) textPages++;
                else if (hasImage(document.getPage(index).getResources(), new HashSet<>(), 0)) imageOnlyPages++;
            }
            double ratio = pages.length == 0 ? 0 : (double) textPages / pages.length;
            double images = pages.length == 0 ? 0 : (double) imageOnlyPages / pages.length;
            DocumentType type = ratio >= .8 ? DocumentType.DIGITAL
                    : ratio <= .1 && images >= .8 ? DocumentType.SCANNED
                    : ratio >= .2 && images >= .2 ? DocumentType.MIXED : DocumentType.UNKNOWN;
            return new DocumentPreflightResult(file, Files.size(file), total, type, anyText,
                    type == DocumentType.SCANNED || type == DocumentType.MIXED, ratio, pages.length,
                    (System.nanoTime() - started) / 1_000_000);
        } catch (IOException error) {
            // A page/font/stream inspection edge case must not prevent conversion.
            throw new IllegalStateException("PDF inspection unavailable: " + file, error);
        }
    }
    private static PDDocument open(Path file) {
        try { return Loader.loadPDF(file.toFile()); }
        catch (InvalidPasswordException error) {
            // A protected PDF is not evidence of corruption. Allow the engine to try it.
            throw new IllegalStateException("PDF protegido; análisis previo no disponible", error);
        } catch (IOException error) {
            throw new ConversionException(Files.isReadable(file) ? ErrorCode.PDF_INVALID : ErrorCode.FILE_INACCESSIBLE,
                    "PDF inspection failed: " + file, error);
        }
    }
    public static int[] samplePages(int total) {
        int count = Math.min(total, MAX_SAMPLE);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = count <= 1 ? 0 : (int)((long)i * (total - 1) / (count - 1));
        return result;
    }
    private static boolean hasImage(PDResources resources, Set<Object> seen, int depth) throws IOException {
        checkInterrupted();
        if (resources == null || depth > 8 || !seen.add(resources.getCOSObject())) return false;
        for (var name : resources.getXObjectNames()) {
            checkInterrupted();
            var object = resources.getXObject(name);
            if (object instanceof PDImageXObject) return true;
            if (object instanceof PDFormXObject form && hasImage(form.getResources(), seen, depth + 1)) return true;
        }
        return false;
    }
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Obsolete preflight");
    }
    private static final class EnoughText extends RuntimeException { }
    private static final class BoundedTextProbe extends PDFTextStripper {
        private int characters;
        BoundedTextProbe() throws IOException { }
        @Override protected void processTextPosition(TextPosition position) {
            checkInterrupted();
            if (position.getUnicode() != null)
                characters += (int) position.getUnicode().codePoints().filter(Character::isLetterOrDigit).count();
            if (characters >= MIN_CHARACTERS) throw new EnoughText();
        }
    }
}
