package model;

import java.security.InvalidParameterException;
import java.util.Arrays;

public class RequestType {
    
    public static class REQUEST_TYPES{
        public static final int GET_FILE_LIST = 1;
        public static final int GET_FILE_SIZE = 2;
        public static final int GET_FILE_DATA = 3;
    }
    
    // 1 byte
    private int requestType;
    // 1 byte
    private int fileId;
    // 4 bytes
    private long startByte;
    // 4 bytes
    private long endByte;
    private byte[] data;
    
    public RequestType(int requestType, int fileId, long startByte, long endByte, byte[] data){
        this.requestType = requestType;
        this.fileId = fileId;
        this.startByte = startByte;
        this.endByte = endByte;
        this.data = data;
    }
    
    public RequestType(byte[] rawData) {
        // request_type:1 byte|file_id:1 byte|start_byte 4 bytes|end_byte 4 bytes
        if (rawData.length < 10){
            throw new InvalidParameterException("Invalid Header");
        }
        requestType = (int)rawData[0] & 0xFF;
        fileId = (int)rawData[1] & 0xFF;
        startByte = 0;
        for(int i = 2; i < 6; i++){
            startByte = (startByte << 8) | ((int)rawData[i] & 0xFF);
        }
        endByte = 0;
        for(int i = 6; i < 10; i++){
            endByte = (endByte << 8) | ((int)rawData[i] & 0xFF);
        }
        data = Arrays.copyOfRange(rawData, 10, rawData.length);
    }
    
    public byte[] toByteArray(){
        int dataLength = (data != null) ? data.length : 0;
        byte[] rawData = new byte[10 + dataLength];
        rawData[0] = (byte)(requestType & 0xFF);
        rawData[1] = (byte)(fileId & 0xFF);
        long tmp = startByte;
        for(int i = 5; i > 1; i--){
            rawData[i] = (byte)(tmp & 0xFF);
            tmp >>= 8;
        }
        tmp = endByte;
        for(int i = 9; i > 5; i--){
            rawData[i] = (byte)(tmp & 0xFF);
            tmp >>= 8;
        }
        if (data != null){
            System.arraycopy(data, 0, rawData, 10, dataLength);
        }
        return rawData;
    }
    
    public int getRequestType() {
        return requestType;
    }

    public int getFileId() {
        return fileId;
    }

    public long getStartByte() {
        return startByte;
    }

    public long getEndByte() {
        return endByte;
    }

    public byte[] getData() {
        return data;
    }
    
    @Override
    public String toString() {
        StringBuilder resultBuf = new StringBuilder("\nrequest_type:" + requestType);
        resultBuf.append("\nfile_id:" + fileId);
        resultBuf.append("\nstart_byte:" + startByte);
        resultBuf.append("\nend_byte:" + endByte);
        resultBuf.append("\ndata:");
        if (data != null){
            for(byte b : data){
                resultBuf.append(b);
            }
        }
        return resultBuf.toString();
    }
} 