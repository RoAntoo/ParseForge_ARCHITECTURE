package dev.parseforge.presentation.javafx;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DownloadEstimateTest {
    @Test void requiresHistoryKnownSizeAndStableSpeed() {
        var estimate = new DownloadEstimate();
        estimate.update("model", 0, 0);
        estimate.update("model", 1000, 1_000_000_000L);
        assertTrue(estimate.remainingSeconds(1000, 10000).isEmpty());
        estimate.update("model", 2000, 2_000_000_000L);
        assertEquals(1000, estimate.bytesPerSecond());
        assertEquals(8, estimate.remainingSeconds(2000, 10000).orElseThrow());
        assertTrue(estimate.remainingSeconds(2000, -1).isEmpty());
        estimate.update("model", 2200, 3_000_000_000L);
        assertTrue(estimate.remainingSeconds(2200, 10000).isEmpty());
    }
    @Test void resetsForNextFileAndDropsOldSamples() {
        var estimate = new DownloadEstimate();
        for(int i=0;i<=10;i++) estimate.update("one",i*1000L,i*1_000_000_000L);
        assertEquals(1000,estimate.bytesPerSecond());
        estimate.update("two",0,11_000_000_000L);
        assertEquals(0,estimate.bytesPerSecond());
        assertTrue(estimate.remainingSeconds(0,10000).isEmpty());
        estimate.update("two",1000,12_000_000_000L);
        estimate.update("two",2000,13_000_000_000L);
        estimate.update("two",0,14_000_000_000L);
        assertEquals(0,estimate.bytesPerSecond());
    }
}
