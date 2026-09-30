package dev.parseforge.application.port.out;
public interface DownloadClient {
    DownloadResult download(DownloadRequest request, DownloadProgressListener listener, OperationCancellation cancellation);
}

