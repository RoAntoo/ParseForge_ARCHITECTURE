package dev.parseforge.infrastructure.engine.marker;

import com.fasterxml.jackson.databind.JsonNode;
import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.infrastructure.engine.EngineFiles;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class ManagedMarkerRuntime implements dev.parseforge.infrastructure.engine.ManagedPythonRuntime {
    public static final String ENTRYPOINT = "from marker.scripts.convert_single import convert_single_cli; convert_single_cli()";
    private final Path root;
    private final JsonNode manifest;
    public ManagedMarkerRuntime(Path root, JsonNode manifest) throws IOException {
        this.root = root.toRealPath();
        this.manifest = manifest;
    }
    public Path root() { return root; }
    public Path resolve(String relative) throws IOException { return EngineFiles.safeResolve(root, relative); }
    public Map<String, String> environment() throws IOException {
        Map<String, String> env = new HashMap<>();
        for (String key : List.of("SystemRoot", "WINDIR", "COMSPEC", "NUMBER_OF_PROCESSORS")) {
            String value = System.getenv(key);
            if (value != null) env.put(key, value);
        }
        Map<String, String> folders = Map.ofEntries(
                Map.entry("HOME", "cache/home"), Map.entry("USERPROFILE", "cache/home"),
                Map.entry("LOCALAPPDATA", "cache/home/AppData/Local"), Map.entry("APPDATA", "cache/home/AppData/Roaming"),
                Map.entry("HF_HOME", "models/huggingface"), Map.entry("HF_HUB_CACHE", "models/huggingface/hub"),
                Map.entry("MODEL_CACHE_DIR", "models/datalab"), Map.entry("TORCH_HOME", "cache/torch"),
                Map.entry("XDG_CACHE_HOME", "cache"), Map.entry("PIP_CACHE_DIR", "cache/pip"),
                Map.entry("TEMP", "temp"), Map.entry("TMP", "temp"));
        for (var entry : folders.entrySet()) {
            Path folder = resolve(entry.getValue());
            Files.createDirectories(folder);
            env.put(entry.getKey(), folder.toString());
        }
        String windows = System.getenv("SystemRoot");
        env.put("PATH", resolve("runtime/python") + ";" + resolve("runtime/llamacpp")
                + (windows == null ? "" : ";" + Path.of(windows, "System32")));
        env.put("LLAMA_CPP_BINARY", resolve(manifest.path("llamaCpp").path("executable").asText()).toString());
        env.put("PYTHONNOUSERSITE", "1"); env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("PYTHONUNBUFFERED", "1"); env.put("PYTHONIOENCODING", "utf-8");
        env.put("TORCH_DEVICE", "cpu"); env.put("SURYA_INFERENCE_BACKEND", "llamacpp");
        env.put("SURYA_INFERENCE_KEEP_ALIVE", "true"); env.put("HF_HUB_DISABLE_SYMLINKS_WARNING", "1");
        env.put("HF_HUB_OFFLINE", "1");
        return Map.copyOf(env);
    }
    public ProcessSpec python(List<String> args, Duration timeout) throws IOException {
        var isolated = new ArrayList<>(List.of("-I", "-X", "utf8", "-u", "-B"));
        isolated.addAll(args);
        return new ProcessSpec(resolve(manifest.path("python").path("executable").asText()),
                isolated, environment(), root, timeout, false);
    }
    public ProcessSpec conversion(dev.parseforge.domain.model.ConversionRequest request) throws IOException {
        dev.parseforge.infrastructure.engine.ConversionStorageGuard.check(request.inputFile(), root);
        var args = new ArrayList<>(List.of("-c", ENTRYPOINT, request.inputFile().toRealPath().toString(),
                "--output_dir", request.outputDirectory().toAbsolutePath().toString(),
                "--output_format", request.outputFormat().commandValue(),
                // pdftext's Windows process pool can fail with invalid queue handles
                // when launched by the desktop app. Keep text extraction in-process.
                "--disable_multiprocessing"));
        if (request.forceOcr()) args.add("--force_ocr");
        return python(args, Duration.ofHours(6));
    }
}
