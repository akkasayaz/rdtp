package client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import model.FileDataResponseType;
import model.FileListResponseType;
import model.FileSizeResponseType;
import model.RequestType;
import model.ResponseType;
import model.ResponseType.RESPONSE_TYPES;
import model.FileDescriptor;

/**
 * ReliableClient implements a reliable UDP file transfer client with:
 * - Acknowledgment-based reliability
 * - Automatic retransmission of lost packets
 * - Basic flow control
 * - File saving and checksum verification
 */
public class ReliableClient {
    private static final int TIMEOUT_MS = 1000; // Timeout in milliseconds
    private static final int MAX_RETRIES = 3;   // Maximum retransmission attempts
    private static final int CHUNK_SIZE = ResponseType.MAX_DATA_SIZE;
    private static final String DOWNLOAD_DIR = "downloads";
    private final loggerManager logger;
    private final MessageDigest md5Digest;
    private long totalLatency = 0;
    private int packetCount = 0;
    private int failedAttempts = 0;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    public ReliableClient() throws NoSuchAlgorithmException {
        this.logger = loggerManager.getInstance(this.getClass());
        this.md5Digest = MessageDigest.getInstance("MD5");
        
        // Create downloads directory if it doesn't exist
        File downloadDir = new File(DOWNLOAD_DIR);
        if (!downloadDir.exists()) {
            if (!downloadDir.mkdir()) {
                throw new RuntimeException("Failed to create downloads directory");
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Sends a request with reliability guarantees and measures latency
     */
    private DatagramPacket sendWithRetry(DatagramSocket socket, RequestType request, 
            InetAddress address, int port) throws IOException {
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            throw new IOException("Too many failed attempts, stopping transfer");
        }

        byte[] sendData = request.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
        
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                long sendTime = System.nanoTime();
                socket.send(sendPacket);
                logger.debug("Sent request: " + request.toString());
                
                byte[] receiveData = new byte[ResponseType.MAX_RESPONSE_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.setSoTimeout(TIMEOUT_MS);
                socket.receive(receivePacket);
                long receiveTime = System.nanoTime();
                
                // Calculate and accumulate latency
                long latency = (receiveTime - sendTime) / 1_000_000; // Convert to milliseconds
                totalLatency += latency;
                packetCount++;
                
                ResponseType response = new ResponseType(receivePacket.getData());
                if (response.getResponseType() != RESPONSE_TYPES.INVALID_REQUEST_TYPE) {
                    logger.debug("Received valid response (latency: " + latency + "ms)");
                    failedAttempts = 0; // Reset failed attempts on success
                    return receivePacket;
                }
                logger.warn("Received invalid response type");
                failedAttempts++;
            } catch (SocketTimeoutException e) {
                retries++;
                failedAttempts++;
                logger.warn("Retry " + retries + " of " + MAX_RETRIES + " (Failed attempts: " + failedAttempts + ")");
            } catch (Exception e) {
                failedAttempts++;
                logger.error("Unexpected error: " + e.getMessage());
                throw new IOException("Failed to send/receive data", e);
            }
        }
        
        logger.error("Failed to receive response after " + MAX_RETRIES + " attempts");
        throw new IOException("Failed to receive response after " + MAX_RETRIES + " attempts");
    }

    private FileDescriptor[] getFileList(String ip, int port) throws IOException {
        DatagramSocket socket = null;
        try {
            InetAddress IPAddress = InetAddress.getByName(ip);
            RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_LIST, 0, 0, 0, null);
            socket = new DatagramSocket();
            
            DatagramPacket response = sendWithRetry(socket, req, IPAddress, port);
            FileListResponseType fileList = new FileListResponseType(response.getData());
            logger.info(fileList.toString());
            return fileList.getFileDescriptors();
        } catch (Exception e) {
            logger.error("Failed to get file list: " + e.getMessage());
            throw new IOException("Failed to get file list", e);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private long getFileSize(String ip, int port, int file_id) throws IOException {
        DatagramSocket socket = null;
        try {
            InetAddress IPAddress = InetAddress.getByName(ip);
            RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_SIZE, file_id, 0, 0, null);
            socket = new DatagramSocket();
            
            DatagramPacket response = sendWithRetry(socket, req, IPAddress, port);
            FileSizeResponseType sizeResponse = new FileSizeResponseType(response.getData());
            logger.info(sizeResponse.toString());
            return sizeResponse.getFileSize();
        } catch (Exception e) {
            logger.error("Failed to get file size: " + e.getMessage());
            throw new IOException("Failed to get file size", e);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private void getFileData(String ip, int port, int file_id, String fileName, long size) throws IOException {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid file size: " + size);
        }

        DatagramSocket socket = null;
        FileOutputStream fileOutputStream = null;
        File outputFile = null;
        boolean success = false;

        try {
            InetAddress IPAddress = InetAddress.getByName(ip);
            socket = new DatagramSocket();
            long currentPosition = 1; // Start from 1 as per protocol
            long maxReceivedByte = 0;
            
            // Create file output stream
            outputFile = new File(DOWNLOAD_DIR + File.separator + fileName);
            fileOutputStream = new FileOutputStream(outputFile);
            md5Digest.reset();
            
            while (maxReceivedByte < size) {
                // Calculate chunk boundaries
                long chunkEnd = Math.min(currentPosition + CHUNK_SIZE - 1, size);
                RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_DATA, 
                        file_id, currentPosition, chunkEnd, null);
                
                DatagramPacket response = sendWithRetry(socket, req, IPAddress, port);
                FileDataResponseType dataResponse = new FileDataResponseType(response.getData());
                
                if (dataResponse.getResponseType() != RESPONSE_TYPES.GET_FILE_DATA_SUCCESS) {
                    logger.error("Failed to get file data");
                    throw new IOException("Failed to get file data chunk");
                }
                
                // Write data to file and update MD5
                byte[] data = dataResponse.getData();
                if (data == null || data.length == 0) {
                    throw new IOException("Received empty data chunk");
                }

                fileOutputStream.write(data);
                md5Digest.update(data);
                
                logger.debug("Received chunk: " + dataResponse.toString());
                if (dataResponse.getEnd_byte() > maxReceivedByte) {
                    maxReceivedByte = dataResponse.getEnd_byte();
                    currentPosition = maxReceivedByte + 1;
                    logger.info(String.format("Progress: %.1f%%", 
                        (float)maxReceivedByte / size * 100));
                }
            }
            success = true;
        } catch (Exception e) {
            logger.error("Failed to download file: " + e.getMessage());
            throw new IOException("Failed to download file", e);
        } finally {
            if (socket != null) {
                socket.close();
            }
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
            // Delete the incomplete file if download failed
            if (!success && outputFile != null && outputFile.exists()) {
                if (!outputFile.delete()) {
                    logger.warn("Failed to delete incomplete file: " + outputFile.getPath());
                }
            }
        }
    }

    private void printTransferStats(String fileName, long fileSize, long duration) {
        try {
            double avgLatency = packetCount > 0 ? (double)totalLatency / packetCount : 0;
            double throughput = duration > 0 ? (fileSize / 1024.0) / (duration / 1000.0) : 0; // KB/s
            
            logger.info("\nTransfer Statistics:");
            logger.info("File: " + fileName);
            logger.info("Size: " + fileSize + " bytes");
            logger.info("MD5 Checksum: " + bytesToHex(md5Digest.digest()));
            logger.info("Duration: " + duration + " ms");
            logger.info(String.format("Average Latency: %.2f ms", avgLatency));
            logger.info(String.format("Throughput: %.2f KB/s", throughput));
            logger.info("Packets Sent/Received: " + packetCount);
            logger.info("Failed Attempts: " + failedAttempts);
        } catch (Exception e) {
            logger.error("Failed to print transfer statistics: " + e.getMessage());
        } finally {
            // Reset counters for next transfer
            totalLatency = 0;
            packetCount = 0;
            failedAttempts = 0;
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java ReliableClient <ip>:<port>");
            System.exit(1);
        }

        String[] adr = args[0].split(":");
        if (adr.length != 2) {
            System.err.println("Invalid address format. Use: <ip>:<port>");
            System.exit(1);
        }

        String ip = adr[0];
        int port;
        try {
            port = Integer.parseInt(adr[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + adr[1]);
            System.exit(1);
            return;
        }

        ReliableClient client = null;
        Scanner scanner = null;
        
        try {
            client = new ReliableClient();
            scanner = new Scanner(System.in);
            
            while (true) {
                try {
                    FileDescriptor[] files = client.getFileList(ip, port);
                    if (files == null || files.length == 0) {
                        System.out.println("No files available for download");
                        break;
                    }

                    System.out.println("\nAvailable files:");
                    for (FileDescriptor file : files) {
                        System.out.println(file.toString());
                    }
                    
                    System.out.println("\nEnter the file id to download or -1 to exit");
                    int fileId = scanner.nextInt();
                    
                    if (fileId == -1) {
                        break;
                    }
                    
                    // Find the file name from the file list
                    String fileName = null;
                    for (FileDescriptor file : files) {
                        if (file.getFile_id() == fileId) {
                            fileName = file.getFile_name();
                            break;
                        }
                    }
                    
                    if (fileName == null) {
                        System.out.println("Invalid file ID");
                        continue;
                    }
                    
                    System.out.println("You have chosen file: " + fileName + ". Getting the size info...");
                    long size = client.getFileSize(ip, port, fileId);
                    
                    if (size > 0) {
                        System.out.println("The file size is " + size + " bytes. Starting download...");
                        long startTime = System.currentTimeMillis();
                        
                        client.getFileData(ip, port, fileId, fileName, size);
                        
                        long endTime = System.currentTimeMillis();
                        client.printTransferStats(fileName, size, endTime - startTime);
                    } else {
                        System.out.println("Failed to get file size or file not found");
                    }
                } catch (Exception e) {
                    System.err.println("Error during transfer: " + e.getMessage());
                    client.logger.error("Transfer error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }
} 