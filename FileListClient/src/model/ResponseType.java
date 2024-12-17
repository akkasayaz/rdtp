package model;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class ResponseType {
    
    public static final int HEADER_SIZE = 10;
    public static int MAX_DATA_SIZE = 1000;
    
    public static class RESPONSE_TYPES{
        public static final int GET_FILE_LIST_SUCCESS = 1;
        public static final int GET_FILE_SIZE_SUCCESS = 2;
        public static final int GET_FILE_DATA_SUCCESS = 3;
        
        public static final int INVALID_REQUEST_TYPE = 100;
        public static final int INVALID_FILE_ID = 101;
        public static final int INVALID_START_OR_END_BYTE = 102;
    }
    
    // 1 byte
    private int responseType;
    // 1 byte
    private int fileId;
    // 4 bytes
    protected long startByte;
    // 4 bytes
    protected long endByte;
    protected byte[] data;
    
    public ResponseType(int responseType, int fileId, long startByte, long endByte, byte[] data){
        this.responseType = responseType;
        this.fileId = fileId;
        this.startByte = startByte;
        this.endByte = endByte;
        this.data = data;
    }
    
    public ResponseType(byte[] rawData) {
        if (rawData.length < HEADER_SIZE){
            throw new InvalidParameterException("Invalid Header");
        }
        responseType = (int)rawData[0] & 0xFF;
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
        rawData[0] = (byte)(responseType & 0xFF);
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
    
    public int getResponseType() {
        return responseType;
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
    
    public static List<FileDescriptor> parseFileList(byte[] data) {
        List<FileDescriptor> fileList = new ArrayList<>();
        if (data.length < 1) return fileList;
        int totalFiles = data[0] & 0xFF;
        int index = 1;
        for(int i = 0; i < totalFiles; i++) {
            if (index + 1 > data.length) break;
            int fileId = data[index++] & 0xFF;
            StringBuilder fileName = new StringBuilder();
            while(index < data.length && data[index] != 0) {
                fileName.append((char)data[index++]);
            }
            index++; // Skip null terminator
            fileList.add(new FileDescriptor(fileId, fileName.toString()));
        }
        return fileList;
    }
    
    public static long parseFileSize(byte[] data) {
        if (data.length < 4) return -1;
        long size = 0;
        for(int i = 0; i < 4; i++){
            size = (size << 8) | ((int)data[i] & 0xFF);
        }
        return size;
    }
    
    @Override
    public String toString() {
        StringBuilder resultBuf = new StringBuilder("\nresponse_type:" + responseType);
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