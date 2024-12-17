package client;

import model.FileDescriptor;
import model.RequestType;
import model.ResponseType;
import utils.MD5Checksum;
import utils.Timer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Scanner;

public class Client {
    private static final int MAX_CONNECTIONS = 2;
    private ConnectionManager connectionManager;
    private Scanner scanner;

    public Client(String[] serverAddresses) throws IOException {
        this.connectionManager = new ConnectionManager(MAX_CONNECTIONS, serverAddresses);
        this.scanner = new Scanner(System.in);
    }

    public void start() throws IOException {
        while (true) {
            List<FileDescriptor> files = getFileList();
            displayFileList(files);
            System.out.print("Enter the file id to download or -1 to exit: ");
            int fileId = scanner.nextInt();
            if (fileId == -1) {
                break;
            }
            FileDescriptor selectedFile = getFileDescriptor(files, fileId);
            if (selectedFile == null) {
                System.out.println("Invalid File ID. Please try again.");
                continue;
            }
            long fileSize = getFileSize(fileId);
            System.out.println("The file size is " + fileSize + " bytes. Starting download...");
            downloadFile(fileId, selectedFile.getFileName(), fileSize);
        }
        scanner.close();
        connectionManager.closeAllConnections();
    }

    private List<FileDescriptor> getFileList() throws IOException {
        ResponseType response = connectionManager.sendRequest(new RequestType(RequestType.REQUEST_TYPES.GET_FILE_LIST, 0, 0, 0, null));
        return ResponseType.parseFileList(response.getData());
    }

    private void displayFileList(List<FileDescriptor> files) {
        System.out.println("File List:");
        for (FileDescriptor file : files) {
            System.out.println(file.getFileId() + "        " + file.getFileName());
        }
    }

    private FileDescriptor getFileDescriptor(List<FileDescriptor> files, int fileId) {
        for (FileDescriptor file : files) {
            if (file.getFileId() == fileId) {
                return file;
            }
        }
        return null;
    }

    private long getFileSize(int fileId) throws IOException {
        RequestType request = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_SIZE, fileId, 0, 0, null);
        ResponseType response = connectionManager.sendRequest(request);
        return ResponseType.parseFileSize(response.getData());
    }

    private void downloadFile(int fileId, String fileName, long fileSize) throws IOException {
        FileDownloader downloader = new FileDownloader(fileId, fileName, fileSize, connectionManager);
        Timer timer = new Timer();
        timer.start();
        downloader.download();
        timer.stop();

        String md5 = MD5Checksum.getMD5Checksum(fileName);
        boolean isValid = md5.equals(downloader.getExpectedMD5());
        System.out.println("Download " + (isValid ? "completed successfully." : "failed due to data corruption."));
        System.out.println("MD5 hash: " + md5);
        System.out.println("Time taken: " + timer.getElapsedTime() + " ms");
        System.out.println("MD5 Verification: " + (isValid ? "Passed" : "Failed"));
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Client server_IP1:port1 server_IP2:port2");
            return;
        }
        try {
            Client client = new Client(args);
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 