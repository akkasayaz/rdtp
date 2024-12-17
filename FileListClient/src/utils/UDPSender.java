package utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class UDPSender {
    private DatagramSocket socket;
    private InetSocketAddress address;
    private byte[] data;

    public UDPSender(DatagramSocket socket, InetSocketAddress address, byte[] data){
        this.socket = socket;
        this.address = address;
        this.data = data;
    }

    public void send() throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, address.getAddress(), address.getPort());
        socket.send(packet);
    }
} 