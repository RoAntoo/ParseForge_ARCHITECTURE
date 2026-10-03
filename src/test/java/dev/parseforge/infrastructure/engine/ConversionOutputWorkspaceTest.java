package dev.parseforge.infrastructure.engine;

import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversionOutputWorkspaceTest {
    @TempDir Path temp;

    private ConversionRequest request() {
        return new ConversionRequest(temp.resolve("document.pdf"), temp.resolve("salida ñ"),
                new EngineId("marker"), OutputFormat.MARKDOWN, true);
    }

    @Test void publishesMarkdownWithImagesAndMetadataAndCleansStaging() throws Exception {
        Path staging;
        try (var workspace = new ConversionOutputWorkspace(request())) {
            staging = workspace.request().outputDirectory();
            assertTrue(workspace.request().forceOcr());
            Path document = Files.createDirectories(staging.resolve("document/images"));
            Files.writeString(document.resolve("page.png"), "image");
            Path markdown = Files.writeString(staging.resolve("document/document.md"), "![page](images/page.png)");
            Files.writeString(staging.resolve("document/document_meta.json"), "{}");
            assertEquals(List.of(request().outputDirectory().resolve("document/document.md")), workspace.publish(markdown));
        }
        assertFalse(Files.exists(staging));
        assertEquals("image", Files.readString(request().outputDirectory().resolve("document/images/page.png")));
        assertEquals("{}", Files.readString(request().outputDirectory().resolve("document/document_meta.json")));
    }

    @Test void emptyMarkdownIsRejectedWithoutTouchingPreviousOutput() throws Exception {
        Path destination = Files.createDirectories(request().outputDirectory());
        Files.writeString(destination.resolve("document.md"), "previous");
        Path staging;
        try (var workspace = new ConversionOutputWorkspace(request())) {
            staging = workspace.request().outputDirectory();
            Path markdown = Files.createFile(staging.resolve("document.md"));
            assertThrows(ConversionException.class, () -> workspace.publish(markdown));
        }
        assertFalse(Files.exists(staging));
        assertEquals("previous", Files.readString(destination.resolve("document.md")));
    }

    @Test void closingUnpublishedConversionDiscardsOnlyItsOwnPartialFiles() throws Exception {
        Path destination = Files.createDirectories(request().outputDirectory());
        Files.writeString(destination.resolve("document.md"), "previous");
        Path staging;
        try (var workspace = new ConversionOutputWorkspace(request())) {
            staging = workspace.request().outputDirectory();
            Files.writeString(staging.resolve("document.md"), "partial");
        }
        assertFalse(Files.exists(staging));
        assertEquals("previous", Files.readString(destination.resolve("document.md")));
    }

    @Test void destinationConflictPreservesGeneratedFilesForRecovery() throws Exception {
        Path destination = Files.createDirectories(request().outputDirectory());
        Files.writeString(destination.resolve("document"), "unrelated file");
        Path staging;
        try (var workspace = new ConversionOutputWorkspace(request())) {
            staging = workspace.request().outputDirectory();
            Files.createDirectory(staging.resolve("document"));
            Path markdown = Files.writeString(staging.resolve("document/document.md"), "converted");
            var error = assertThrows(ConversionException.class, () -> workspace.publish(markdown));
            assertTrue(error.getMessage().contains(staging.toString()));
        }
        assertEquals("converted", Files.readString(staging.resolve("document/document.md")));
        assertEquals("unrelated file", Files.readString(destination.resolve("document")));
    }
}
