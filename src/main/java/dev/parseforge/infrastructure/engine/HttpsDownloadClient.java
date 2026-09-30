package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.exception.EngineInstallException;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import static dev.parseforge.domain.exception.EngineInstallException.Code.*;

public final class HttpsDownloadClient implements DownloadClient {
    @FunctionalInterface interface Transport { CompletableFuture<HttpResponse<InputStream>> send(HttpRequest request); }
    private final Transport transport;
    private final ChecksumVerifier checksums;
    public HttpsDownloadClient(ChecksumVerifier checksums) {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(30)).build();
        this.transport = request -> client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        this.checksums = checksums;
    }
    HttpsDownloadClient(Transport transport, ChecksumVerifier checksums) { this.transport = transport; this.checksums = checksums; }
    @Override public DownloadResult download(DownloadRequest request, DownloadProgressListener listener,
                                             OperationCancellation cancellation) {
        Path destination = request.destination();
        Path partial = destination.resolveSibling(destination.getFileName() + ".part");
        try {
            cancellation.check();
            EngineFiles.safeResolve(destination.getParent(), destination.getFileName().toString());
            Files.createDirectories(destination.getParent());
            if (Files.isSymbolicLink(destination) || Files.isSymbolicLink(partial)) throw new IOException("Archivo redirigido");
            Files.deleteIfExists(partial);
            if (Files.isRegularFile(destination)) {
                try {
                    checksums.verify(destination, request.sha256(), cancellation);
                    return new DownloadResult(destination, Files.size(destination));
                } catch (EngineInstallException mismatch) {
                    if (mismatch.code() != CHECKSUM_MISMATCH) throw mismatch;
                    Files.delete(destination);
                }
            }
            URI uri = request.uri();
            HttpResponse<InputStream> response = null;
            for (int redirect = 0; redirect <= 8; redirect++) {
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                    throw new IOException("La descarga requiere HTTPS");
                var future = transport.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(30))
                        .header("User-Agent", "ParseForge/0.1").GET().build());
                try (var registration = cancellation.onCancel(() -> future.cancel(true))) { response = future.get(); }
                if (cancellation.isCancelled()) { response.body().close(); cancellation.check(); }
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    try (var body = response.body()) {
                        uri = uri.resolve(response.headers().firstValue("location").orElseThrow(() -> new IOException("Redirect sin destino")));
                    }
                } else break;
            }
            if (response == null || response.statusCode() != 200) {
                if (response != null) response.body().close();
                throw new IOException("HTTP " + (response == null ? "sin respuesta" : response.statusCode()));
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            long bytes = 0;
            try (var input = response.body();
                 var registration = cancellation.onCancel(() -> { try { input.close(); } catch (IOException ignored) { } });
                 var output = Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[65536];
                int count;
                long lastProgress = 0;
                listener.onProgress(0, total);
                while ((count = input.read(buffer)) != -1) {
                    cancellation.check(); output.write(buffer, 0, count); bytes += count;
                    if (System.nanoTime() - lastProgress > 100_000_000L) {
                        listener.onProgress(bytes, total); lastProgress = System.nanoTime();
                    }
                }
            }
            cancellation.check();
            if (total >= 0 && bytes != total) throw new IOException("Descarga incompleta");
            if (request.expectedBytes() > 0 && bytes != request.expectedBytes()) throw new IOException("Tamaño de descarga incorrecto");
            checksums.verify(partial, request.sha256(), cancellation);
            cancellation.check();
            Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING);
            listener.onProgress(bytes, total);
            return new DownloadResult(destination, bytes);
        } catch (EngineInstallException error) { throw error; }
        catch (Exception error) {
            cancellation.check();
            if (error instanceof InterruptedException) { Thread.currentThread().interrupt(); cancellation.check(); }
            throw new EngineInstallException(error instanceof AccessDeniedException ? PERMISSION_DENIED : DOWNLOAD_FAILED,
                    "No se pudo descargar " + destination.getFileName() + ". Revisá la conexión y reintentá.", error);
        } finally {
            try { Files.deleteIfExists(partial); } catch (IOException ignored) { }
        }
    }
}
