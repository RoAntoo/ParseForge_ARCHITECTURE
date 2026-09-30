package dev.parseforge.infrastructure.engine;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.zip.ZipInputStream;
import dev.parseforge.application.port.out.OperationCancellation;

public final class EngineFiles {
    private EngineFiles() { }
    /** Validates each parent once during a read-only verification or deletion. */
    public static final class CheckedTree {
        private final Path root;
        private final java.util.Set<Path> checked = new java.util.HashSet<>();
        public CheckedTree(Path root) throws IOException {
            this.root = root.toAbsolutePath().normalize();
            if (!this.root.toRealPath().equals(this.root)) throw new IOException("Root redirigido");
            checked.add(this.root);
        }
        public Path resolve(String relative) throws IOException {
            Path path = root.resolve(relative).normalize();
            if (Path.of(relative).isAbsolute() || !path.startsWith(root) || path.equals(root))
                throw new IOException("Ruta fuera del motor");
            checkDirectory(path.getParent());
            if (Files.isSymbolicLink(path)) throw new IOException("Archivo redirigido");
            return path;
        }
        private void checkDirectory(Path directory) throws IOException {
            if (checked.contains(directory)) return;
            checkDirectory(directory.getParent());
            if (!directory.toRealPath().equals(directory)) throw new IOException("Carpeta redirigida");
            checked.add(directory);
        }
    }
    public static Path safeResolve(Path root, String relative) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        Path result = absolute.resolve(relative).normalize();
        if (relative.isBlank() || Path.of(relative).isAbsolute() || !result.startsWith(absolute) || result.equals(absolute))
            throw new IOException("Ruta fuera del motor: " + relative);
        // A real-path comparison on the closest existing entry also resolves all
        // ancestors. Avoid re-statting every ancestor for each of 29,000 files.
        Path current = result;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) current = current.getParent();
        if (current != null && (Files.isSymbolicLink(current) || !current.toRealPath().equals(current)))
            throw new IOException("Ruta redirigida: " + current);
        return result;
    }
    /** No following links; validate the entire tree before the first deletion. */
    public static void deleteTree(Path allowedParent, Path target) throws IOException {
        Path safe = safeResolve(allowedParent, allowedParent.toAbsolutePath().normalize()
                .relativize(target.toAbsolutePath().normalize()).toString());
        if (!Files.exists(safe, LinkOption.NOFOLLOW_LINKS)) return;
        java.util.List<Path> entries;
        try (var paths = Files.walk(safe)) { entries = paths.sorted(Comparator.reverseOrder()).toList(); }
        CheckedTree tree = new CheckedTree(allowedParent);
        for (Path entry : entries) {
            Path verified = tree.resolve(allowedParent.toAbsolutePath().normalize().relativize(entry).toString());
            if (Files.isDirectory(verified, LinkOption.NOFOLLOW_LINKS) && !verified.toRealPath().equals(verified))
                throw new IOException("Carpeta redirigida");
        }
        for (Path entry : entries) Files.delete(entry);
    }
    public static void extract(Path archive, Path destination, OperationCancellation cancellation) throws IOException {
        Files.createDirectories(destination);
        long extracted = 0;
        int count = 0;
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                cancellation.check();
                if (++count > 100_000) throw new IOException("Demasiados archivos en ZIP");
                Path path = safeResolve(destination, entry.getName().replace('\\', '/'));
                if (entry.isDirectory()) { Files.createDirectories(path); continue; }
                Files.createDirectories(path.getParent());
                try (var output = Files.newOutputStream(path)) {
                    byte[] buffer = new byte[65536];
                    int bytes;
                    while ((bytes = zip.read(buffer)) != -1) {
                        cancellation.check();
                        if ((extracted += bytes) > 4_000_000_000L) throw new IOException("ZIP excede el límite");
                        output.write(buffer, 0, bytes);
                    }
                }
            }
        }
    }
}
