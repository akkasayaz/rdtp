package client;

import java.io.IOException;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import model.FileDescriptor;
import model.FileListResponseType;
import org.apache.log4j.Logger;

/**
 * ReliableClient implements a reliable file transfer client using dual UDP connections
 * Provides fault tolerance and improved throughput through parallel downloads
 */
public class ReliableClient {
    private static final Logger logger = loggerManager.getInstance(ReliableClient.class);
    private ConnectionManager connectionManager;
    private Scanner scanner;
    private FileDownloader fileDownloader;
    private static final int BUFFER_SIZE = 8192;
    
    // Connection details
    private final String ip1;
    private final int port1;
    private final String ip2;
    private final int port2;
    
    /**
     * Constructor initializes the client with two server endpoints
     */
    public ReliableClient(String ip1, int port1, String ip2, int port2) {
        this.ip1 = ip1;
        this.port1 = port1;
        this.ip2 = ip2;
        this.port2 = port2;
        this.scanner = new Scanner(System.in);
        initializeConnections();
    }
    
    /**
     * Initializes connections to both server endpoints
     */
    private void initializeConnections() {
        try {
            logger.info("Initializing connections to servers...");
            this.connectionManager = new ConnectionManager(
                InetAddress.getByName(ip1), port1,
                InetAddress.getByName(ip2), port2
            );
            logger.info("Connections initialized successfully");
        } catch (IOException e) {
            logger.error("Failed to initialize connections: " + e.getMessage());
            throw new RuntimeException("Connection initialization failed", e);
        }
    }

    /**
     * Gets and displays the list of available files from server
     * @return Array of FileDescriptor objects
     */
    private FileDescriptor[] getFileList() {
        try {
            FileListResponseType response = connectionManager.requestFileList();
            FileDescriptor[] files = response.getFileDescriptors();
            
            System.out.println("\nAvailable Files:");
            for (FileDescriptor file : files) {
                System.out.println(file.getFile_id() + " " + file.getFile_name());
            }
            return files;
        } catch (IOException e) {
            logger.error("Failed to get file list: " + e.getMessage());
            return null;
        }
    }

    /**
     * Main client loop - handles user interaction and file downloads
     */
    public void start() {
        while (true) {
            FileDescriptor[] files = getFileList();
            if (files == null || files.length == 0) {
                logger.error("No files available or couldn't retrieve file list");
                break;
            }

            System.out.print("\nEnter the file id to download or -1 to exit: ");
            int fileId = scanner.nextInt();
            
            if (fileId == -1) {
                break;
            }

            downloadFile(fileId);
        }
        
        cleanup();
    }

    /**
     * Handles the download of a specific file
     * @param fileId ID of the file to download
     */
    private void downloadFile(int fileId) {
        try {
            // Get file size
            long fileSize = connectionManager.getFileSize(fileId);
            System.out.println("File size: " + fileSize + " bytes");
            
            // Start download with timing
            long startTime = System.currentTimeMillis();
            byte[] fileData = connectionManager.downloadFile(fileId, fileSize);
            long endTime = System.currentTimeMillis();
            
            if (fileData != null) {
                // Save file and verify
                String fileName = "downloaded_" + fileId;
                saveAndVerifyFile(fileName, fileData);
                
                // Print statistics
                printDownloadStats(fileSize, endTime - startTime);
            }
        } catch (IOException e) {
            logger.error("Download failed: " + e.getMessage());
        }
    }

    /**
     * Saves downloaded file and verifies its integrity
     */
    private void saveAndVerifyFile(String fileName, byte[] fileData) {
        try {
            // Save file
            Path filePath = Paths.get(fileName);
            Files.write(filePath, fileData);
            
            // Calculate MD5
            String md5Hash = calculateMD5(fileData);
            System.out.println("Download complete. MD5 hash: " + md5Hash);
            
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Failed to save or verify file: " + e.getMessage());
        }
    }

    /**
     * Calculates MD5 hash of file data
     */
    private String calculateMD5(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(data);
        
        // Convert to hex string
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Prints download statistics
     */
    private void printDownloadStats(long fileSize, long timeMs) {
        double speedMbps = (fileSize * 8.0 / (1024*1024)) / (timeMs / 1000.0);
        System.out.printf("Download completed in %.2f seconds\n", timeMs/1000.0);
        System.out.printf("Average speed: %.2f Mbps\n", speedMbps);
        // Additional statistics from ConnectionManager
        connectionManager.printStatistics();
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        scanner.close();
        connectionManager.close();
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ReliableClient <ip1:port1> <ip2:port2>");
            return;
        }

        try {
            String[] conn1 = args[0].split(":");
            String[] conn2 = args[1].split(":");
            
            ReliableClient client = new ReliableClient(
                conn1[0], Integer.parseInt(conn1[1]),
                conn2[0], Integer.parseInt(conn2[1])
            );
            
            client.start();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}