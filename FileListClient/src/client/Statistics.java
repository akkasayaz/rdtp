package client;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import org.apache.log4j.Logger;

/**
 * Tracks and analyzes download performance statistics
 */
public class Statistics {
    private static final Logger logger = loggerManager.getInstance(Statistics.class);
    
    private final AtomicLong bytesTransferred;
    private final AtomicLong startTime;
    private final ConcurrentHashMap<Integer, ConnectionStats> connectionStats;
    private final int updateInterval = 1000; // ms
    private long lastUpdateTime;
    private long lastBytesTransferred;

    public Statistics() {
        this.bytesTransferred = new AtomicLong(0);
        this.startTime = new AtomicLong(System.currentTimeMillis());
        this.connectionStats = new ConcurrentHashMap<>();
        this.lastUpdateTime = System.currentTimeMillis();
        this.lastBytesTransferred = 0;
    }

    /**
     * Inner class to track per-connection statistics
     */
    private static class ConnectionStats {
        AtomicLong bytesTransferred = new AtomicLong(0);
        AtomicLong packetsReceived = new AtomicLong(0);
        AtomicLong packetsLost = new AtomicLong(0);
        AtomicLong totalRtt = new AtomicLong(0);

        void updateRtt(long rtt) {
            totalRtt.addAndGet(rtt);
            packetsReceived.incrementAndGet();
        }

        double getAverageRtt() {
            long packets = packetsReceived.get();
            return packets > 0 ? totalRtt.get() / (double)packets : 0;
        }

        double getLossRate() {
            long total = packetsReceived.get() + packetsLost.get();
            return total > 0 ? (packetsLost.get() * 100.0) / total : 0;
        }
    }

    /**
     * Update statistics with new data
     */
    public void updateProgress(int connectionId, long bytes, long rtt, boolean packetLost) {
        bytesTransferred.addAndGet(bytes);
        
        ConnectionStats stats = connectionStats.computeIfAbsent(
            connectionId, k -> new ConnectionStats());
        
        if (packetLost) {
            stats.packetsLost.incrementAndGet();
        } else {
            stats.bytesTransferred.addAndGet(bytes);
            stats.updateRtt(rtt);
        }

        // Print progress if interval elapsed
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > updateInterval) {
            printProgress();
            lastUpdateTime = now;
        }
    }

    /**
     * Print current progress and speed
     */
    private void printProgress() {
        long currentTotal = bytesTransferred.get();
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - lastUpdateTime;
        long bytesDelta = currentTotal - lastBytesTransferred;

        double speedMbps = (bytesDelta * 8.0 / (1024*1024)) / (timeDelta / 1000.0);
        
        System.out.printf("\rCurrent speed: %.2f Mbps, Total: %.2f MB",
            speedMbps, currentTotal / (1024.0 * 1024.0));
        
        lastBytesTransferred = currentTotal;
    }

    /**
     * Generate final statistics report
     */
    public String generateReport() {
        long totalTime = System.currentTimeMillis() - startTime.get();
        double avgSpeedMbps = (bytesTransferred.get() * 8.0 / (1024*1024)) / (totalTime / 1000.0);
        
        StringBuilder report = new StringBuilder();
        report.append("\n=== Download Statistics ===\n");
        report.append(String.format("Total time: %.2f seconds\n", totalTime / 1000.0));
        report.append(String.format("Average speed: %.2f Mbps\n", avgSpeedMbps));
        report.append(String.format("Total data transferred: %.2f MB\n", 
            bytesTransferred.get() / (1024.0 * 1024.0)));

        // Per-connection statistics
        connectionStats.forEach((connId, stats) -> {
            report.append(String.format("\nConnection %d:\n", connId));
            report.append(String.format("  Bytes transferred: %.2f MB\n", 
                stats.bytesTransferred.get() / (1024.0 * 1024.0)));
            report.append(String.format("  Average RTT: %.2f ms\n", stats.getAverageRtt()));
            report.append(String.format("  Packet loss rate: %.2f%%\n", stats.getLossRate()));
        });

        return report.toString();
    }
}