package dev.parseforge.infrastructure.engine.markitdown;

import com.fasterxml.jackson.databind.JsonNode;
import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.infrastructure.engine.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class ManagedMarkItDownRuntime implements ManagedPythonRuntime {
    // The upstream CLI can fall back to text for malformed PDFs. Validate the
    // PDF container first, then use its documented CLI and explicit output.
    public static final String ENTRYPOINT = "import sys; from pdfminer.pdfparser import PDFParser; from pdfminer.pdfdocument import PDFDocument; f=open(sys.argv[1],'rb'); PDFDocument(PDFParser(f)); f.close(); from markitdown.__main__ import main; main()";
    private final Path root;
    private final JsonNode manifest;
    public ManagedMarkItDownRuntime(Path root, JsonNode manifest) throws IOException {
        this.root = root.toRealPath(); this.manifest = manifest;
    }
    public Map<String, String> environment() throws IOException {
        Map<String, String> env = new HashMap<>();
        for (String key : List.of("SystemRoot", "WINDIR", "COMSPEC", "NUMBER_OF_PROCESSORS")) {
            if (System.getenv(key) != null) env.put(key, System.getenv(key));
        }
        for (var entry : Map.of("HOME", "cache/home", "USERPROFILE", "cache/home", "LOCALAPPDATA", "cache/home/AppData/Local",
                "APPDATA", "cache/home/AppData/Roaming", "PIP_CACHE_DIR", "cache/pip", "TEMP", "temp", "TMP", "temp").entrySet()) {
            Path folder = EngineFiles.safeResolve(root, entry.getValue()); Files.createDirectories(folder);
            env.put(entry.getKey(), folder.toString());
        }
        env.put("PATH", EngineFiles.safeResolve(root, "runtime/python").toString());
        env.put("PYTHONNOUSERSITE", "1"); env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("PYTHONUNBUFFERED", "1"); env.put("PYTHONIOENCODING", "utf-8");
        return Map.copyOf(env);
    }
    public ProcessSpec python(List<String> args, Duration timeout) throws IOException {
        var isolated = new ArrayList<>(List.of("-I", "-X", "utf8", "-u", "-B")); isolated.addAll(args);
        return new ProcessSpec(EngineFiles.safeResolve(root, manifest.path("python").path("executable").asText()),
                isolated, environment(), root, timeout, false);
    }
    public static Path output(ConversionRequest request) {
        String filename = request.inputFile().getFileName().toString();
        String stem = filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
                ? filename.substring(0, filename.length() - 4) : filename;
        return request.outputDirectory().toAbsolutePath().normalize().resolve(stem + ".md");
    }
    public ProcessSpec conversion(ConversionRequest request) throws IOException {
        if (request.forceOcr()) throw new IllegalArgumentException("MarkItDown no admite Forzar OCR.");
        return python(List.of("-c", ENTRYPOINT, request.inputFile().toRealPath().toString(), "-o", output(request).toString()), Duration.ofHours(1));
    }
}
