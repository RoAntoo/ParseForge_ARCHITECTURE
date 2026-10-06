package dev.parseforge.domain.model;

import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.exception.ErrorCode;
import java.nio.file.Path;
import java.util.*;

/** Formats supported by the bundled, offline engine profiles. */
public final class DocumentFormats {
    private DocumentFormats() { }
    private static final List<String> IMAGES = List.of("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff", "webp");
    private static final List<String> MARKER = java.util.stream.Stream.concat(java.util.stream.Stream.of("pdf"), IMAGES.stream()).toList();
    private static final List<String> MARKITDOWN = List.of("pdf", "docx", "epub", "pptx", "xlsx", "xls", "html", "htm", "txt", "md", "csv", "json", "xml", "msg", "zip");
    public static String extension(Path path) {
        if (path == null || path.getFileName() == null) return "";
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    public static List<String> extensions(EngineId engine) {
        if (engine == null) return List.of();
        return switch (engine.value()) {
            case "marker" -> MARKER;
            case "markitdown" -> MARKITDOWN;
            default -> List.of();
        };
    }
    public static List<String> allExtensions() {
        return java.util.stream.Stream.concat(MARKER.stream(), MARKITDOWN.stream()).distinct().toList();
    }
    public static boolean supported(Path path) { return allExtensions().contains(extension(path)); }
    public static boolean supports(EngineId engine, Path path) { return extensions(engine).contains(extension(path)); }
    public static boolean isPdf(Path path) { return extension(path).equals("pdf"); }
    public static boolean isImage(Path path) { return IMAGES.contains(extension(path)); }
    public static void requireSupported(ConversionRequest request) {
        if (!supports(request.engineId(), request.inputFile()))
            throw new ConversionException(ErrorCode.FORMAT_UNSUPPORTED, "El motor no admite el formato ." + extension(request.inputFile()));
    }
}
