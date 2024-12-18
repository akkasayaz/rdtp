package client;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.log4j.Logger;

import model.ResponseType;

/**
 * Handles file download coordination and chunk management
 * Implements parallel download strategy
 */
public class FileDownloader {
    private static final Logger logger = loggerManager.getInstance(FileDownloader.class);
    
    private final ConnectionManager connectionManager;
    private final int fileId;
    private final long fileSize;
    private final Statistics stats;
    
    // Chunk management
    private static final int DEFAULT_CHUNK_SIZE = ResponseType.MAX_DATA_SIZE;
    private final BlockingQueue<ChunkRequest> pendingChunks;
    private final ConcurrentHashMap<Long, byte[]> completedChunks;
    
    // Thread management
    private final ExecutorService downloadExecutor;
    private final int numThreads = 2; // One per connection
    
    public FileDownloader(ConnectionManager connectionManager, int fileId, long fileSize) {
        this.connectionManager = connectionManager;
        this.fileId = fileId;
        this.fileSize = fileSize;
        this.stats = new Statistics();
        
        this.pendingChunks = new LinkedBlockingQueue<>();
        this.completedChunks = new ConcurrentHashMap<>();
        this.downloadExecutor = Executors.newFixedThreadPool(numThreads);
        
        initializeChunks();
    }

    /**
     * Split file into chunks for download
     */
    private void initializeChunks() {
        long currentOffset = 1; // 1-based indexing
        while (currentOffset <= fileSize) {
            long endOffset = Math.min(currentOffset + DEFAULT_CHUNK_SIZE - 1, fileSize);
            pendingChunks.offer(new ChunkRequest(currentOffset, endOffset));
            currentOffset = endOffset + 1;
        }
        logger.info("Initialized " + pendingChunks.size() + " chunks for download");
    }

    /**
     * Start parallel download
     */
    public byte[] downloadFile() throws IOException, InterruptedException {
        logger.info("Starting parallel download for file " + fileId);
        
        // Start download workers
        Future<?>[] downloadTasks = new Future[numThreads];
        for (int i = 0; i < numThreads; i++) {
            downloadTasks[i] = downloadExecutor.submit(new DownloadWorker());
        }
        
        // Wait for all downloads to complete
        try {
            for (Future<?> task : downloadTasks) {
                task.get();
            }
        } catch (ExecutionException e) {
            throw new IOException("Download failed", e.getCause());
        } finally {
            downloadExecutor.shutdown();
        }
        
        return assembleFile();
    }

    /**
     * Worker class for parallel downloads
     */
    private class DownloadWorker implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    ChunkRequest chunk = pendingChunks.poll();
                    if (chunk == null) break;
                    
                    downloadChunk(chunk);
                }
            } catch (Exception e) {
                logger.error("Download worker failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Download individual chunk
     */
    private void downloadChunk(ChunkRequest chunk) throws IOException {
        byte[] data = connectionManager.downloadChunk(fileId, chunk.startOffset, chunk.endOffset);
        completedChunks.put(chunk.startOffset, data);
        stats.updateProgress(data.length, chunk.startOffset, chunk.endOffset, true);
    }

    /**
     * Assemble downloaded chunks into complete file
     */
    private byte[] assembleFile() {
        byte[] completeFile = new byte[(int)fileSize];
        long currentOffset = 1;
        
        while (currentOffset <= fileSize) {
            byte[] chunk = completedChunks.get(currentOffset);
            System.arraycopy(chunk, 0, completeFile, (int)(currentOffset - 1), chunk.length);
            currentOffset += chunk.length;
        }
        
        return completeFile;
    }

    /**
     * Inner class for chunk requests
     */
    private static class ChunkRequest {
        final long startOffset;
        final long endOffset;
        
        ChunkRequest(long startOffset, long endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }
}