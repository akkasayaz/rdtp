package model;

public class FileDescriptor {
    private int fileId;
    private String fileName;

    public FileDescriptor(int fileId, String fileName) {
        this.fileId = fileId;
        this.fileName = fileName;
    }

    public int getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public String toString() {
        return fileId + "-" + fileName;
    }
} 