package client;

import model.RequestType;
import model.ResponseType;
import model.FileDataResponseType;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class FileDownloader {
    private int fileId;
    private String fileName;
    private long fileSize;
    private ConnectionManager connectionManager;
    private ConcurrentHashMap<Long, byte[]> fileDataMap;
    private AtomicLong bytesReceived;
    private String expectedMD5;

    public FileDownloader(int fileId, String fileName, long fileSize, ConnectionManager connectionManager) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.connectionManager = connectionManager;
        this.fileDataMap = new ConcurrentHashMap<>();
        this.bytesReceived = new AtomicLong(0);
        this.expectedMD5 = "";
    }

    public void download() throws IOException {
        int segmentSize = ResponseType.MAX_DATA_SIZE;
        long segments = (fileSize + segmentSize - 1) / segmentSize;

        for (long i = 0; i < segments; i++) {
            long startByte = i * segmentSize + 1;
            long endByte = Math.min((i + 1) * segmentSize, fileSize);
            RequestType request = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_DATA, fileId, startByte, endByte, null);
            ResponseType response = connectionManager.sendRequest(request);
            if (response.getResponseType() == ResponseType.RESPONSE_TYPES.GET_FILE_DATA_SUCCESS) {
                FileDataResponseType dataResponse = new FileDataResponseType(response.toByteArray());
                fileDataMap.put(dataResponse.getStartByte(), dataResponse.getData());
                bytesReceived.addAndGet(dataResponse.getData().length);
                // Optionally, implement acknowledgment and retransmission logic here
            } else {
                System.out.println("Error in receiving data for segment: " + i);
                // Implement retransmission or error handling
            }
        }

        assembleFile();
        this.expectedMD5 = MD5Checksum.getMD5Checksum(fileName);
    }

    private void assembleFile() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            for (long i = 1; i <= fileSize; i++) {
                // Find the byte corresponding to the current position
                byte[] data = fileDataMap.get(i);
                if (data != null) {
                    fos.write(data);
                }
            }
        }
    }

    public String getExpectedMD5() {
        return expectedMD5;
    }
} 