package client;

import model.RequestType;
import model.ResponseType;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ConnectionManager {
    private List<DatagramSocket> sockets;
    private List<InetSocketAddress> serverAddresses;
    private int currentConnection;

    public ConnectionManager(int maxConnections, String[] serverAddressesInput) throws IOException {
        this.sockets = new ArrayList<>();
        this.serverAddresses = new ArrayList<>();
        this.currentConnection = 0;

        for (int i = 0; i < maxConnections && i < serverAddressesInput.length; i++) {
            String[] parts = serverAddressesInput[i].split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);
            InetSocketAddress address = new InetSocketAddress(ip, port);
            this.serverAddresses.add(address);
            DatagramSocket socket = new DatagramSocket();
            sockets.add(socket);
        }
    }

    public synchronized DatagramSocket getNextSocket() {
        DatagramSocket socket = sockets.get(currentConnection);
        currentConnection = (currentConnection + 1) % sockets.size();
        return socket;
    }

    public ResponseType sendRequest(RequestType request) throws IOException {
        DatagramSocket socket = getNextSocket();
        InetSocketAddress address = serverAddresses.get(sockets.indexOf(socket));

        byte[] sendData = request.toByteArray();
        UDPSender sender = new UDPSender(socket, address, sendData);
        sender.send();

        byte[] receiveData = new byte[ResponseType.MAX_RESPONSE_SIZE()];
        UDPReceiver receiver = new UDPReceiver(socket, receiveData);
        ResponseType response = receiver.receive();

        return response;
    }

    public void closeAllConnections() {
        for (DatagramSocket socket : sockets) {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
} 