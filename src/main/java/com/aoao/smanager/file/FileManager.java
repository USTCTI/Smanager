package com.aoao.smanager.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class FileManager {
    private final Logger logger;
    private final Gson gson;
    private final Path serverRoot;
    
    public FileManager(Logger logger) {
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.serverRoot = Paths.get(".").toAbsolutePath();
    }
    
    public String listFiles(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return createErrorResponse("路径不存在: " + path);
            }
            if (!Files.isDirectory(targetPath)) {
                return createErrorResponse("不是目录: " + path);
            }
            
            List<FileInfo> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetPath)) {
                for (Path entry : stream) {
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    files.add(new FileInfo(
                        entry.getFileName().toString(),
                        entry.toAbsolutePath().toString(),
                        attrs.isDirectory(),
                        attrs.size(),
                        attrs.lastModifiedTime().toMillis(),
                        getFilePermissions(entry)
                    ));
                }
            }
            
            files.sort((a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            
            return gson.toJson(new FileListResponse(true, files, targetPath.toAbsolutePath().toString()));
        } catch (Exception e) {
            logger.error("列出文件失败: " + path, e);
            return createErrorResponse("列出文件失败: " + e.getMessage());
        }
    }
    
    public String readFile(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return createErrorResponse("文件不存在: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return createErrorResponse("不能读取目录: " + path);
            }
            
            long size = Files.size(targetPath);
            if (size > 10 * 1024 * 1024) { // 10MB限制
                return createErrorResponse("文件过大，无法预览: " + size + " bytes");
            }
            
            String content = Files.readString(targetPath);
            BasicFileAttributes attrs = Files.readAttributes(targetPath, BasicFileAttributes.class);
            
            return gson.toJson(new FileContentResponse(true, content, 
                targetPath.getFileName().toString(),
                targetPath.toAbsolutePath().toString(),
                size,
                attrs.lastModifiedTime().toMillis()));
        } catch (Exception e) {
            logger.error("读取文件失败: " + path, e);
            return createErrorResponse("读取文件失败: " + e.getMessage());
        }
    }
    
    public String writeFile(String path, String content) {
        try {
            Path targetPath = resolvePath(path);
            
            if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
                return createErrorResponse("不能写入目录: " + path);
            }
            
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            return gson.toJson(new BasicResponse(true, "文件保存成功"));
        } catch (Exception e) {
            logger.error("写入文件失败: " + path, e);
            return createErrorResponse("写入文件失败: " + e.getMessage());
        }
    }
    
    public String createFile(String path, boolean isDirectory) {
        try {
            Path targetPath = resolvePath(path);
            
            if (Files.exists(targetPath)) {
                return createErrorResponse("文件已存在: " + path);
            }
            
            if (isDirectory) {
                Files.createDirectories(targetPath);
                return gson.toJson(new BasicResponse(true, "目录创建成功"));
            } else {
                Files.createDirectories(targetPath.getParent());
                Files.createFile(targetPath);
                return gson.toJson(new BasicResponse(true, "文件创建成功"));
            }
        } catch (Exception e) {
            logger.error("创建文件失败: " + path, e);
            return createErrorResponse("创建文件失败: " + e.getMessage());
        }
    }
    
    public String deleteFile(String path) {
        try {
            Path targetPath = resolvePath(path);
            
            if (!Files.exists(targetPath)) {
                return createErrorResponse("文件不存在: " + path);
            }
            
            if (Files.isDirectory(targetPath)) {
                Files.walkFileTree(targetPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.delete(targetPath);
            }
            
            return gson.toJson(new BasicResponse(true, "删除成功"));
        } catch (Exception e) {
            logger.error("删除文件失败: " + path, e);
            return createErrorResponse("删除文件失败: " + e.getMessage());
        }
    }
    
    public String renameFile(String path, String newName) {
        try {
            Path targetPath = resolvePath(path);
            Path newPath = targetPath.resolveSibling(newName);
            
            if (!Files.exists(targetPath)) {
                return createErrorResponse("文件不存在: " + path);
            }
            if (Files.exists(newPath)) {
                return createErrorResponse("目标文件已存在: " + newName);
            }
            
            Files.move(targetPath, newPath);
            return gson.toJson(new BasicResponse(true, "重命名成功"));
        } catch (Exception e) {
            logger.error("重命名文件失败: " + path, e);
            return createErrorResponse("重命名文件失败: " + e.getMessage());
        }
    }
    
    private Path resolvePath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return serverRoot;
        }
        
        Path resolved = serverRoot.resolve(path).normalize();
        
        if (!resolved.startsWith(serverRoot)) {
            throw new SecurityException("访问路径超出服务器根目录");
        }
        
        return resolved;
    }
    
    private String getFilePermissions(Path path) {
        try {
            // 跨平台权限检查
            StringBuilder permissions = new StringBuilder();
            
            if (Files.isReadable(path)) permissions.append("r");
            if (Files.isWritable(path)) permissions.append("w");
            if (Files.isExecutable(path)) permissions.append("x");
            
            // 添加文件属性信息
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            if (attrs.isDirectory()) permissions.append("d");
            if (attrs.isRegularFile()) permissions.append("f");
            if (attrs.isSymbolicLink()) permissions.append("l");
            
            return permissions.length() > 0 ? permissions.toString() : "---";
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String createErrorResponse(String message) {
        return gson.toJson(new BasicResponse(false, message));
    }
    
    public static class FileInfo {
        private final String name;
        private final String path;
        private final boolean isDirectory;
        private final long size;
        private final long modifiedTime;
        private final String permissions;
        
        public FileInfo(String name, String path, boolean isDirectory, long size, long modifiedTime, String permissions) {
            this.name = name;
            this.path = path;
            this.isDirectory = isDirectory;
            this.size = size;
            this.modifiedTime = modifiedTime;
            this.permissions = permissions;
        }
        
        public String getName() { return name; }
        public String getPath() { return path; }
        public boolean isDirectory() { return isDirectory; }
        public long getSize() { return size; }
        public long getModifiedTime() { return modifiedTime; }
        public String getPermissions() { return permissions; }
    }
    
    public static class FileListResponse extends BasicResponse {
        private final List<FileInfo> files;
        private final String currentPath;
        
        public FileListResponse(boolean success, List<FileInfo> files, String currentPath) {
            super(success, success ? "成功" : "失败");
            this.files = files;
            this.currentPath = currentPath;
        }
        
        public List<FileInfo> getFiles() { return files; }
        public String getCurrentPath() { return currentPath; }
    }
    
    public static class FileContentResponse extends BasicResponse {
        private final String content;
        private final String fileName;
        private final String filePath;
        private final long fileSize;
        private final long modifiedTime;
        
        public FileContentResponse(boolean success, String content, String fileName, String filePath, long fileSize, long modifiedTime) {
            super(success, success ? "成功" : "失败");
            this.content = content;
            this.fileName = fileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.modifiedTime = modifiedTime;
        }
        
        public String getContent() { return content; }
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public long getFileSize() { return fileSize; }
        public long getModifiedTime() { return modifiedTime; }
    }
    
    public static class BasicResponse {
        private final boolean success;
        private final String message;
        
        public BasicResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}