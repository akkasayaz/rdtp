package client;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;

import model.ResponseType;

/**
 * Implements adaptive download strategy based on connection performance
 */
public class DownloadStrategy {
    private static final Logger logger = loggerManager.getInstance(DownloadStrategy.class);
    
    private final Map<Integer, ConnectionMetrics> connectionMetrics;
    private final AtomicInteger activeConnection;
    
    // Configuration
    private static final int RTT_WEIGHT = 70;
    private static final int LOSS_WEIGHT = 30;
    private static final double RTT_THRESHOLD = 100.0; // ms
    private static final double LOSS_THRESHOLD = 5.0; // percent

    public DownloadStrategy() {
        this.connectionMetrics = new ConcurrentHashMap<>();
        this.activeConnection = new AtomicInteger(0);
    }

    /**
     * Inner class to track connection metrics
     */
    private static class ConnectionMetrics {
        double averageRtt;
        double lossRate;
        double score;
        int consecutiveFailures;

        void updateMetrics(double rtt, double loss) {
            this.averageRtt = rtt;
            this.lossRate = loss;
            calculateScore();
        }

        void calculateScore() {
            // Lower score is better
            double rttScore = Math.min(averageRtt / RTT_THRESHOLD, 1.0);
            double lossScore = Math.min(lossRate / LOSS_THRESHOLD, 1.0);
            this.score = (rttScore * RTT_WEIGHT + lossScore * LOSS_WEIGHT) / 100.0;
        }
    }

    /**
     * Update connection metrics and adjust strategy
     */
    public void updateMetrics(int connectionId, double rtt, double lossRate) {
        ConnectionMetrics metrics = connectionMetrics.computeIfAbsent(
            connectionId, k -> new ConnectionMetrics());
        
        metrics.updateMetrics(rtt, lossRate);
        logger.debug(String.format("Connection %d metrics - RTT: %.2fms, Loss: %.2f%%, Score: %.2f",
            connectionId, rtt, lossRate, metrics.score));
    }

    /**
     * Choose best connection for next chunk
     */
    public int selectConnection() {
        int bestConn = -1;
        double bestScore = Double.MAX_VALUE;

        for (Map.Entry<Integer, ConnectionMetrics> entry : connectionMetrics.entrySet()) {
            ConnectionMetrics metrics = entry.getValue();
            if (metrics.score < bestScore && metrics.consecutiveFailures < 3) {
                bestScore = metrics.score;
                bestConn = entry.getKey();
            }
        }

        // Fallback to round-robin if no clear winner
        if (bestConn == -1) {
            bestConn = activeConnection.incrementAndGet() % connectionMetrics.size();
        }

        return bestConn;
    }

    /**
     * Calculate optimal chunk size based on connection performance
     */
    public int calculateChunkSize(int connectionId) {
        ConnectionMetrics metrics = connectionMetrics.get(connectionId);
        if (metrics == null) return ResponseType.MAX_DATA_SIZE;

        // Adjust chunk size based on RTT and loss rate
        double rttFactor = Math.max(0.5, Math.min(1.0, RTT_THRESHOLD / metrics.averageRtt));
        double lossFactor = Math.max(0.5, Math.min(1.0, 1.0 - (metrics.lossRate / 100.0)));
        
        int baseChunkSize = ResponseType.MAX_DATA_SIZE;
        int adjustedSize = (int)(baseChunkSize * rttFactor * lossFactor);
        
        // Ensure chunk size stays within reasonable bounds
        return Math.max(baseChunkSize / 2, Math.min(adjustedSize, baseChunkSize));
    }

    /**
     * Record connection failure
     */
    public void recordFailure(int connectionId) {
        ConnectionMetrics metrics = connectionMetrics.get(connectionId);
        if (metrics != null) {
            metrics.consecutiveFailures++;
            if (metrics.consecutiveFailures >= 3) {
                logger.warn("Connection " + connectionId + " marked as potentially failed");
            }
        }
    }

    /**
     * Record connection success
     */
    public void recordSuccess(int connectionId) {
        ConnectionMetrics metrics = connectionMetrics.get(connectionId);
        if (metrics != null) {
            metrics.consecutiveFailures = 0;
        }
    }
}