package dev.parseforge.infrastructure.document;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Synthetic documents only; no personal books or external source content. */
public final class PdfFixtures {
    public static Path create(Path file, String... pages) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (var document = new PDDocument()) {
            var image = new BufferedImage(1000, 1300, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 1000, 1300);
            graphics.setColor(Color.BLACK); graphics.setFont(new Font("Arial", Font.PLAIN, 30));
            for (int line = 0; line < 12; line++) graphics.drawString("Readable Markdown - scanned sample line " + line, 40, 100 + 70 * line);
            graphics.dispose();
            var picture = LosslessFactory.createFromImage(document, image);
            for (String kind : pages) {
                var page = new PDPage(); document.addPage(page);
                try (var content = new PDPageContentStream(document, page)) {
                    if (kind.equals("image")) content.drawImage(picture, 0, 0, 612, 792);
                    else if (!kind.equals("blank")) {
                        content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(40, 700);
                        content.showText(kind.equals("sparse") ? "1" : "Readable Markdown - local synthetic document for preflight and conversion tests.");
                        content.endText();
                    }
                }
            }
            document.save(file.toFile());
        }
        return file;
    }
    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0]);
        create(folder.resolve("digital-text.pdf"), "text", "text");
        create(folder.resolve("scanned-image-only.pdf"), "image", "image");
        create(folder.resolve("mixed.pdf"), "text", "image");
        create(folder.resolve("blank.pdf"), "blank");
        Files.writeString(folder.resolve("corrupt.pdf"), "%PDF-1.7\nThis is deliberately corrupt.");
        if (args.length > 1) {
            String[] pages = new String[240]; java.util.Arrays.fill(pages, "text"); create(folder.resolve("digital-long.pdf"), pages);
            java.util.Arrays.fill(pages, "image"); create(folder.resolve("scanned-long.pdf"), pages);
        }
    }
}
