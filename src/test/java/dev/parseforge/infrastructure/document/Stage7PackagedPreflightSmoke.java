package dev.parseforge.infrastructure.document;

import java.nio.file.Path;
import dev.parseforge.domain.model.DocumentType;

/** Run with the packaged runtime/app jars plus this test class, no Maven libraries. */
public final class Stage7PackagedPreflightSmoke {
    public static void main(String[] args) {
        var folder = Path.of(args[0]);
        String[] names = {"digital-text", "scanned-image-only", "mixed", "blank"};
        DocumentType[] types = {DocumentType.DIGITAL, DocumentType.SCANNED, DocumentType.MIXED, DocumentType.UNKNOWN};
        for (int i = 0; i < names.length; i++) {
            var result = new PdfBoxDocumentPreflight().inspect(folder.resolve(names[i] + ".pdf"));
            if (result.type() != types[i]) throw new AssertionError(result);
            System.out.println("PACKAGED PREFLIGHT PASS " + names[i] + " " + result.type());
        }
        System.out.println("java.home=" + System.getProperty("java.home"));
    }
}
