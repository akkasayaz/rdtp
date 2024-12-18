package client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.BitSet;
import org.apache.log4j.Logger;

/**
 * Implements sliding window protocol for reliable data transfer
 */
public class WindowManager {
    private static final Logger logger = loggerManager.getInstance(WindowManager.class);
    
    // Window management
    private final AtomicInteger baseSequence;
    private final AtomicInteger nextSequence;
    private final BitSet receivedPackets;
    private final ConcurrentHashMap<Integer, byte[]> packetBuffer;
    
    // Configuration
    private int windowSize;
    private static final int MIN_WINDOW_SIZE = 4;
    private static final int MAX_WINDOW_SIZE = 32;
    private static final double GROWTH_FACTOR = 1.5;
    private static final double SHRINK_FACTOR = 0.7;

    public WindowManager() {
        this.baseSequence = new AtomicInteger(0);
        this.nextSequence = new AtomicInteger(0);
        this.receivedPackets = new BitSet();
        this.packetBuffer = new ConcurrentHashMap<>();
        this.windowSize = MIN_WINDOW_SIZE;
    }

    /**
     * Check if window has space for new packets
     */
    public synchronized boolean hasSpace() {
        return nextSequence.get() - baseSequence.get() < windowSize;
    }

    /**
     * Get next sequence number for sending
     */
    public int getNextSequence() {
        return nextSequence.getAndIncrement();
    }

    /**
     * Record received packet
     */
    public synchronized void recordPacket(int sequence, byte[] data) {
        if (sequence >= baseSequence.get()) {
            receivedPackets.set(sequence - baseSequence.get());
            packetBuffer.put(sequence, data);
            slideWindow();
        }
    }

    /**
     * Slide window when possible
     */
    private void slideWindow() {
        int current = 0;
        while (receivedPackets.get(current)) {
            receivedPackets.clear(current);
            packetBuffer.remove(baseSequence.get() + current);
            current++;
        }
        
        if (current > 0) {
            baseSequence.addAndGet(current);
            // Increase window size on successful transmission
            adjustWindowSize(true);
        }
    }

    /**
     * Adjust window size based on network conditions
     */
    public synchronized void adjustWindowSize(boolean success) {
        if (success) {
            // Increase window size
            windowSize = Math.min(MAX_WINDOW_SIZE, 
                (int)(windowSize * GROWTH_FACTOR));
        } else {
            // Decrease window size
            windowSize = Math.max(MIN_WINDOW_SIZE, 
                (int)(windowSize * SHRINK_FACTOR));
        }
        logger.debug("Adjusted window size to: " + windowSize);
    }

    /**
     * Get missing packets in current window
     */
    public synchronized int[] getMissingPackets() {
        int windowEnd = nextSequence.get() - baseSequence.get();
        BitSet missing = new BitSet(windowEnd);
        missing.set(0, windowEnd);
        missing.andNot(receivedPackets);
        
        int[] missingSeq = new int[missing.cardinality()];
        int j = 0;
        for (int i = missing.nextSetBit(0); i >= 0; i = missing.nextSetBit(i+1)) {
            missingSeq[j++] = baseSequence.get() + i;
        }
        return missingSeq;
    }

    /**
     * Get current window size
     */
    public int getWindowSize() {
        return windowSize;
    }

    /**
     * Check if all packets up to sequence have been received
     */
    public synchronized boolean isComplete(int sequence) {
        if (sequence < baseSequence.get()) return true;
        if (sequence >= nextSequence.get()) return false;
        
        BitSet check = new BitSet(sequence - baseSequence.get() + 1);
        check.set(0, sequence - baseSequence.get() + 1);
        check.andNot(receivedPackets);
        return check.isEmpty();
    }
}