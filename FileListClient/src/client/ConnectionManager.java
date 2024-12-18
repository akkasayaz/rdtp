package client;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.log4j.Logger;

import model.FileDataResponseType;
import model.FileListResponseType;
import model.FileSizeResponseType;
import model.RequestType;
import model.ResponseType;

import client.loggerManager;
import client.UDPConnection;

/**
 * Manages dual UDP connections for reliable file transfer
 * Handles connection health monitoring and load balancing
 */
public class ConnectionManager {
    private static final Logger logger = loggerManager.getInstance(ConnectionManager.class);
    
    private final UDPConnection connection1;
    private final UDPConnection connection2;
    
    // Statistics tracking
    private final AtomicLong totalBytesReceived;
    private final AtomicLong totalPacketsReceived;
    private final ConcurrentHashMap<Integer, Long> requestTimes;
    
    // Configuration
    private static final int DEFAULT_TIMEOUT = 5000; // ms
    private static final int MAX_RETRIES = 3;

    /**
     * Initialize dual connections
     */
    public ConnectionManager(InetAddress addr1, int port1, InetAddress addr2, int port2) 
            throws IOException {
        this.connection1 = new UDPConnection(addr1, port1, "Connection-1");
        this.connection2 = new UDPConnection(addr2, port2, "Connection-2");
        
        this.totalBytesReceived = new AtomicLong(0);
        this.totalPacketsReceived = new AtomicLong(0);
        this.requestTimes = new ConcurrentHashMap<>();
        
        logger.info("ConnectionManager initialized with endpoints: " +
                   addr1 + ":" + port1 + " and " + addr2 + ":" + port2);
    }

    /**
     * Request file list from server
     * Tries both connections until successful
     */
    public FileListResponseType requestFileList() throws IOException {
        RequestType request = new RequestType(
            RequestType.REQUEST_TYPES.GET_FILE_LIST, 0, 0, 0, null);
        
        // Try primary connection first
        try {
            return (FileListResponseType) sendRequest(connection1, request);
        } catch (IOException e) {
            logger.warn("Primary connection failed for file list, trying secondary");
            return (FileListResponseType) sendRequest(connection2, request);
        }
    }

    /**
     * Get file size from server
     */
    public long getFileSize(int fileId) throws IOException {
        RequestType request = new RequestType(
            RequestType.REQUEST_TYPES.GET_FILE_SIZE, fileId, 0, 0, null);
        
        FileSizeResponseType response = (FileSizeResponseType) sendRequest(connection1, request);
        return response.getFileSize();
    }

    /**
     * Download file using both connections
     */
    public byte[] downloadFile(int fileId, long fileSize) throws IOException {
        // Create buffer for complete file
        byte[] fileData = new byte[(int)fileSize];
        long startTime = System.currentTimeMillis();
        
        // Split file into chunks
        int chunkSize = ResponseType.MAX_DATA_SIZE;
        long remainingBytes = fileSize;
        long currentOffset = 1; // 1-based indexing as per protocol
        
        while (remainingBytes > 0) {
            long currentChunkSize = Math.min(chunkSize, remainingBytes);
            
            // Alternate between connections for load balancing
            UDPConnection conn = shouldUsePrimaryConnection() ? connection1 : connection2;
            
            // Request chunk
            RequestType request = new RequestType(
                RequestType.REQUEST_TYPES.GET_FILE_DATA,
                fileId,
                currentOffset,
                currentOffset + currentChunkSize - 1,
                null
            );
            
            FileDataResponseType response = (FileDataResponseType) sendRequest(conn, request);
            
            // Copy data to buffer
            System.arraycopy(
                response.getData(), 0,
                fileData, (int)(currentOffset - 1),
                response.getData().length
            );
            
            // Update progress
            currentOffset += currentChunkSize;
            remainingBytes -= currentChunkSize;
            updateStats(currentChunkSize);
            
            // Log progress
            if (remainingBytes % (chunkSize * 10) == 0) {
                double progress = 100.0 * (fileSize - remainingBytes) / fileSize;
                logger.info(String.format("Download progress: %.2f%%", progress));
            }
        }
        
        return fileData;
    }

    /**
     * Send request and receive response with retry logic
     */
    private ResponseType sendRequest(UDPConnection connection, RequestType request) 
            throws IOException {
        IOException lastException = null;
        
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                long startTime = System.currentTimeMillis();
                ResponseType response = connection.sendRequest(request);
                long endTime = System.currentTimeMillis();
                
                // Update timing statistics
                requestTimes.put(request.getRequestType(), endTime - startTime);
                
                return response;
            } catch (IOException e) {
                lastException = e;
                logger.warn(String.format("Attempt %d failed: %s", 
                    attempt + 1, e.getMessage()));
            }
        }
        
        throw lastException;
    }

    /**
     * Determine which connection to use based on performance
     */
    private boolean shouldUsePrimaryConnection() {
        // Simple alternating strategy - can be enhanced with performance metrics
        return totalPacketsReceived.incrementAndGet() % 2 == 0;
    }

    /**
     * Update statistics
     */
    private void updateStats(long bytes) {
        totalBytesReceived.addAndGet(bytes);
    }

    /**
     * Print connection statistics
     */
    public void printStatistics() {
        System.out.println("\nConnection Statistics:");
        System.out.println("Total bytes received: " + totalBytesReceived.get());
        System.out.println("Total packets received: " + totalPacketsReceived.get());
        
        // Print average response times per request type
        System.out.println("\nAverage Response Times (ms):");
        requestTimes.forEach((requestType, time) -> 
            System.out.println("Request type " + requestType + ": " + time + "ms")
        );
        
        // Print per-connection statistics
        connection1.printStats();
        connection2.printStats();
    }

    /**
     * Clean up resources
     */
    public void close() {
        connection1.close();
        connection2.close();
    }

    /**
     * Downloads a specific chunk of the file
     */
    public byte[] downloadChunk(int fileId, long startOffset, long endOffset) throws IOException {
        RequestType request = new RequestType(
            RequestType.REQUEST_TYPES.GET_FILE_DATA,
            fileId,
            startOffset,
            endOffset,
            null
        );
        
        // Try both connections based on strategy
        IOException lastException = null;
        for (UDPConnection conn : new UDPConnection[]{connection1, connection2}) {
            try {
                FileDataResponseType response = (FileDataResponseType) sendRequest(conn, request);
                return response.getData();
            } catch (IOException e) {
                lastException = e;
                logger.warn("Chunk download failed on " + conn + ", trying next connection");
            }
        }
        
        throw new IOException("Failed to download chunk", lastException);
    }


}