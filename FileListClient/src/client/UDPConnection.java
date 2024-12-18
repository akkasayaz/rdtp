package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.log4j.Logger;

import model.ResponseType;
import model.RequestType;
import model.FileListResponseType;
import model.FileSizeResponseType;
import model.FileDataResponseType;

/**
 * Handles single UDP connection with reliability features
 * Implements packet sending/receiving with error handling
 */
public class UDPConnection {
    private static final Logger logger = loggerManager.getInstance(UDPConnection.class);
    
    private final DatagramSocket socket;
    private final InetAddress serverAddress;
    private final int serverPort;
    private final String connectionName;
    
    // Statistics
    private final AtomicInteger packetsLost;
    private final AtomicInteger packetsSent;
    private final AtomicInteger packetsReceived;
    private final AtomicLong totalBytes;
    private final AtomicLong totalRtt; // Round-trip time
    
    // Configuration
    private static final int SOCKET_TIMEOUT = 5000; // ms
    private static final int MAX_PACKET_SIZE = ResponseType.MAX_RESPONSE_SIZE;

    /**
     * Initialize UDP connection
     */
    public UDPConnection(InetAddress address, int port, String name) throws IOException {
        this.serverAddress = address;
        this.serverPort = port;
        this.connectionName = name;
        
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(SOCKET_TIMEOUT);
        
        this.packetsLost = new AtomicInteger(0);
        this.packetsSent = new AtomicInteger(0);
        this.packetsReceived = new AtomicInteger(0);
        this.totalBytes = new AtomicLong(0);
        this.totalRtt = new AtomicLong(0);
        
        logger.info(String.format("UDP Connection '%s' initialized to %s:%d",
            name, address.getHostAddress(), port));
    }


    /**
     * Create appropriate response type based on request type
     */
    private ResponseType createResponse(RequestType request, byte[] data) {
        switch (request.getRequestType()) {
            case RequestType.REQUEST_TYPES.GET_FILE_LIST:
                return new FileListResponseType(data);
            case RequestType.REQUEST_TYPES.GET_FILE_SIZE:
                return new FileSizeResponseType(data);
            case RequestType.REQUEST_TYPES.GET_FILE_DATA:
                return new FileDataResponseType(data);
            default:
                return new ResponseType(data);
        }
    }

    /**
     * Send request and receive response
     */
    public ResponseType sendRequest(RequestType request) throws IOException {
        byte[] sendData = request.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(
            sendData, sendData.length, serverAddress, serverPort);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Send packet
            socket.send(sendPacket);
            packetsSent.incrementAndGet();
            totalBytes.addAndGet(sendData.length);
            
            // Prepare for response
            byte[] receiveData = new byte[MAX_PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            
            // Receive response
            socket.receive(receivePacket);
            packetsReceived.incrementAndGet();
            totalBytes.addAndGet(receivePacket.getLength());
            
            // Update RTT statistics
            long rtt = System.currentTimeMillis() - startTime;
            totalRtt.addAndGet(rtt);
            
            // Create appropriate response type based on request
            return createResponse(request, receivePacket.getData());
            
        } catch (SocketTimeoutException e) {
            packetsLost.incrementAndGet();
            throw new IOException("Request timed out", e);
        }
    }

    /**
     * Print statistics for this connection
     */
    public void printStats() {
        System.out.println("\nStatistics for " + connectionName + ":");
        System.out.println("Packets sent: " + packetsSent.get());
        System.out.println("Packets received: " + packetsReceived.get());
        System.out.println("Packets lost: " + packetsLost.get());
        System.out.println("Total bytes transferred: " + totalBytes.get());
        
        long avgRtt = packetsReceived.get() > 0 ? 
            totalRtt.get() / packetsReceived.get() : 0;
        System.out.println("Average RTT: " + avgRtt + "ms");
        
        double lossRate = packetsSent.get() > 0 ? 
            (packetsLost.get() * 100.0) / packetsSent.get() : 0;
        System.out.printf("Packet loss rate: %.2f%%\n", lossRate);
    }

    /**
     * Close the connection and clean up resources
     */
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public String toString() {
        return connectionName + " (" + serverAddress + ":" + serverPort + ")";
    }

}