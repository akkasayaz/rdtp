package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

import model.FileDataResponseType;
import model.FileListResponseType;
import model.FileSizeResponseType;
import model.RequestType;
import model.ResponseType;
import model.ResponseType.RESPONSE_TYPES;
import client.loggerManager;

public class dummyClient {

    private void sendInvalidRequest(String ip, int port) throws IOException {
        DatagramSocket dsocket = null;
        try {
            InetAddress IPAddress = InetAddress.getByName(ip);
            RequestType req = new RequestType(4, 0, 0, 0, null);
            byte[] sendData = req.toByteArray();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
            dsocket = new DatagramSocket();
            dsocket.send(sendPacket);
            byte[] receiveData = new byte[ResponseType.MAX_RESPONSE_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            dsocket.receive(receivePacket);
            ResponseType response = new ResponseType(receivePacket.getData());
            loggerManager.getInstance(this.getClass()).debug(response.toString());
        } finally {
            if (dsocket != null) {
                dsocket.close();
            }
        }
    }


    private void getFileList(String ip, int port) throws IOException {
        DatagramSocket dsocket = null;
        try {
            InetAddress IPAddress = InetAddress.getByName(ip);
            RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_LIST, 0, 0, 0, null);
            byte[] sendData = req.toByteArray();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
            dsocket = new DatagramSocket();
            dsocket.send(sendPacket);
            byte[] receiveData = new byte[ResponseType.MAX_RESPONSE_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            dsocket.receive(receivePacket);
            FileListResponseType response = new FileListResponseType(receivePacket.getData());
        } finally {
            if (dsocket != null) {
                dsocket.close();
            }
        }
    }


    private long getFileSize(String ip, int port, int file_id) throws IOException {
        DatagramSocket dsocket = null;
        try {
            InetAddress IPAddress = InetAddress.getByName(ip);
            RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_SIZE, file_id, 0, 0, null);
            byte[] sendData = req.toByteArray();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
            dsocket = new DatagramSocket();
            dsocket.send(sendPacket);
            byte[] receiveData = new byte[ResponseType.MAX_RESPONSE_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            dsocket.receive(receivePacket);
            FileSizeResponseType response = new FileSizeResponseType(receivePacket.getData());
            loggerManager.getInstance(this.getClass()).debug(response.toString());
            return response.getFileSize();
        } finally {
            if (dsocket != null) {
                dsocket.close();
            }
        }
    }

    private void getFileData(String ip, int port, int file_id, long start, long end) throws IOException {
        DatagramSocket dsocket = null;
        try {
            InetAddress IPAddress = InetAddress.getByName(ip);
            RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_DATA, file_id, start, end, null);
            byte[] sendData = req.toByteArray();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
            dsocket = new DatagramSocket();
            dsocket.send(sendPacket);
            byte[] receiveData = new byte[ResponseType.MAX_RESPONSE_SIZE];
            long maxReceivedByte = -1;
            while (maxReceivedByte < end) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                dsocket.receive(receivePacket);
                FileDataResponseType response = new FileDataResponseType(receivePacket.getData());
                if (response.getResponseType() != RESPONSE_TYPES.GET_FILE_DATA_SUCCESS) {
                    break;
                }
                if (response.getEnd_byte() > maxReceivedByte) {
                    maxReceivedByte = response.getEnd_byte();
                }
            }
        } finally {
            if (dsocket != null) {
                dsocket.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("ip:port is mandatory");
        }
        System.out.println(args.length);
        String[] adr1 = args[0].split(":");
        String ip1 = adr1[0];
        int port1 = Integer.parseInt(adr1[1]);
        String[] adr2 = args[1].split(":");
        String ip2 = adr2[0];
        int port2 = Integer.parseInt(adr2[1]);


        dummyClient inst = new dummyClient();

        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            inst.getFileList(ip1, port1);
            System.out.println("Enter the file id to download or -1 to exit");
            int fileId = scanner.nextInt();
            if (fileId == -1) {
                break;
            }
            System.out.println("You have chosen file number " + fileId + ". Getting the size info...");
            long size = inst.getFileSize(ip1, port1, fileId);
            System.out.println("The file size is " + size + " bytes. Starting download...");
            long startTime = System.currentTimeMillis();


            inst.getFileData(ip1, port1, fileId, 1, size);

            // calculate the time taken to download the file
            long endTime = System.currentTimeMillis();
            long timeTaken = endTime - startTime;
            System.out.println("Time taken to download the file is " + timeTaken + " milliseconds");
        }

        scanner.close();
    }
}
