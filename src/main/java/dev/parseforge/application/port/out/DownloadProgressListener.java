package dev.parseforge.application.port.out;
@FunctionalInterface public interface DownloadProgressListener { void onProgress(long bytes, long total); }

