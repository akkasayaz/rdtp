package utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import model.ResponseType;

public class UDPReceiver {
    private DatagramSocket socket;
    private byte[] buffer;

    public UDPReceiver(DatagramSocket socket, byte[] buffer){
        this.socket = socket;
        this.buffer = buffer;
    }

    public ResponseType receive() throws IOException {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return new ResponseType(packet.getData());
    }
} 