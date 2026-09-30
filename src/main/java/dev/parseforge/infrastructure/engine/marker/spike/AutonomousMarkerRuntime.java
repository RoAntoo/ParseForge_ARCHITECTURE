package dev.parseforge.infrastructure.engine.marker.spike;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.parseforge.application.port.out.ProcessSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Experimental Stage 3B descriptor, deliberately outside the UI/engine registry. */
public final class AutonomousMarkerRuntime {
    public static final String ENTRYPOINT = "from marker.scripts.convert_single import convert_single_cli; convert_single_cli()";
    private final Path root;
    private final JsonNode manifest;

    public AutonomousMarkerRuntime(Path root) throws IOException {
        this.root = root.toRealPath();
        this.manifest = new ObjectMapper().readTree(this.root.resolve("engine.json").toFile());
        if (!"marker".equals(manifest.path("id").asText())
                || !"windows-x64".equals(manifest.path("platform").asText())) {
            throw new IOException("Unsupported engine manifest");
        }
    }

    public Path root() { return root; }

    public Path resolve(String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (relative.isBlank() || Path.of(relative).isAbsolute() || !path.startsWith(root) || path.equals(root)) {
            throw new IOException("Manifest path escapes engine: " + relative);
        }
        Path real = path.toRealPath();
        if (!real.startsWith(root)) { throw new IOException("Engine link escapes root: " + relative); }
        return real;
    }

    public void verifyHashes() throws IOException {
        if (!manifest.path("criticalFiles").isArray() || manifest.path("criticalFiles").isEmpty()) {
            throw new IOException("Missing critical file inventory");
        }
        for (JsonNode entry : manifest.path("criticalFiles")) {
            checkHash(resolve(entry.path("path").asText()), entry.path("sha256").asText());
        }
        for (String component : List.of("python", "llamaCpp")) {
            JsonNode entry = manifest.path(component);
            checkHash(resolve(entry.path("executable").asText()), entry.path("executableSha256").asText());
        }
    }

    private void checkHash(Path path, String expected) throws IOException {
        try (var input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected)) {
                throw new IOException("Hash mismatch: " + path);
            }
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public Map<String, String> environment() throws IOException {
        Map<String, String> env = new HashMap<>();
        // Explicit allowlist: no inherited PYTHON*, HF*, service URLs, or global PATH.
        for (String key : List.of("SystemRoot", "WINDIR", "COMSPEC", "NUMBER_OF_PROCESSORS")) {
            String value = System.getenv(key);
            if (value != null) { env.put(key, value); }
        }
        Map<String, String> privatePaths = Map.ofEntries(
                Map.entry("HOME", "cache/home"), Map.entry("USERPROFILE", "cache/home"),
                Map.entry("LOCALAPPDATA", "cache/home/AppData/Local"), Map.entry("APPDATA", "cache/home/AppData/Roaming"),
                Map.entry("HF_HOME", "models/huggingface"), Map.entry("HF_HUB_CACHE", "models/huggingface/hub"),
                Map.entry("MODEL_CACHE_DIR", "models/datalab"), Map.entry("TORCH_HOME", "cache/torch"),
                Map.entry("XDG_CACHE_HOME", "cache"), Map.entry("PIP_CACHE_DIR", "cache/pip"),
                Map.entry("TEMP", "temp"), Map.entry("TMP", "temp"));
        for (var entry : privatePaths.entrySet()) {
            Path folder = root.resolve(entry.getValue());
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(root)) { throw new IOException("Private cache escapes root"); }
            env.put(entry.getKey(), folder.toString());
        }
        env.put("PATH", resolve("runtime/python").toString() + ";"
                + resolve(manifest.path("llamaCpp").path("executable").asText()).getParent()
                + ";" + Path.of(System.getenv("SystemRoot"), "System32"));
        env.put("LLAMA_CPP_BINARY", resolve(manifest.path("llamaCpp").path("executable").asText()).toString());
        env.put("PYTHONNOUSERSITE", "1");
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("PYTHONUNBUFFERED", "1");
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("TORCH_DEVICE", "cpu");
        env.put("SURYA_INFERENCE_BACKEND", "llamacpp");
        env.put("SURYA_INFERENCE_KEEP_ALIVE", "true");
        env.put("HF_HUB_DISABLE_SYMLINKS_WARNING", "1");
        return Map.copyOf(env);
    }

    public ProcessSpec python(List<String> args, Duration timeout) throws IOException {
        List<String> isolatedArgs = new ArrayList<>(List.of("-I", "-X", "utf8", "-u", "-B"));
        isolatedArgs.addAll(args);
        return new ProcessSpec(resolve(manifest.path("python").path("executable").asText()),
                isolatedArgs, environment(), root, timeout, false);
    }

    public ProcessSpec conversion(Path pdf, Path output, boolean ocr) throws IOException {
        List<String> args = new ArrayList<>(List.of("-c", ENTRYPOINT,
                pdf.toRealPath().toString(), "--output_dir", output.toAbsolutePath().toString(),
                "--output_format", "markdown"));
        if (ocr) { args.add("--force_ocr"); }
        return python(args, Duration.ofMinutes(15));
    }
}
