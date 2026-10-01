package dev.parseforge.presentation.javafx;

import java.util.ArrayDeque;
import java.util.OptionalLong;

/** Five-second rolling window; ETA needs two seconds and reasonably stable rates. */
public final class DownloadEstimate {
    private record Sample(long nanos, long bytes) { }
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private String file = "";
    private double speed;
    private boolean stable;
    public void update(String file, long bytes, long nanos) {
        if (!this.file.equals(file) || (!samples.isEmpty() && bytes < samples.getLast().bytes())) {
            samples.clear(); speed = 0; stable = false; this.file = file;
        }
        if (!samples.isEmpty() && nanos <= samples.getLast().nanos()) return;
        samples.addLast(new Sample(nanos, bytes));
        while (samples.size() > 2 && nanos - samples.getFirst().nanos() > 5_000_000_000L) samples.removeFirst();
        Sample first = samples.getFirst();
        double seconds = (nanos - first.nanos()) / 1e9;
        speed = seconds >= 1 ? (bytes - first.bytes()) / seconds : 0;
        double min = Double.POSITIVE_INFINITY, max = 0;
        Sample previous = null;
        for (Sample sample : samples) {
            if (previous != null) {
                double rate = (sample.bytes() - previous.bytes()) / ((sample.nanos() - previous.nanos()) / 1e9);
                min = Math.min(min, rate); max = Math.max(max, rate);
            }
            previous = sample;
        }
        stable = samples.size() >= 3 && seconds >= 2 && min > 0 && max / min <= 3;
    }
    public double bytesPerSecond() { return speed; }
    public OptionalLong remainingSeconds(long completed, long total) {
        if (total <= 0 || speed <= 0 || !stable) return OptionalLong.empty();
        return OptionalLong.of((long) Math.ceil(Math.max(0, total - completed) / speed));
    }
    public void reset() { samples.clear(); file = ""; speed = 0; stable = false; }
}
