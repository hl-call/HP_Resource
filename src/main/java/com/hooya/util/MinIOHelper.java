package com.hooya.util;

import com.sun.jndi.toolkit.url.Uri;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class MinIOHelper {

    private final String endpoint = "https://minio.nbhooya.net";
    private final String accessKey = "iLHj2Bvgd0iAQR0ztC7P";
    private final String secretKey = "T0yZVLPA1eJPc0nWWbRpR87dACnG0V0gta2R1Qnz";

    @Value("${destPath}")
    private String destPath;


    private final MinioClient minioClient;

    public MinIOHelper() {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public String uploadToMinIO(String filePath,String localFilePath) {
   //     String localFilePath = "";
        // 判断文件是否存在
        File file = new File(filePath);
        if (!file.exists()) {
            return "文件不存在";
        }
        try {
            String bucketName = "bpmpod";



            // 获取扩展名和文件名
            String extension = getFileExtension(filePath);
        //    String fileName = UUID.randomUUID() + extension;



            // 文件路径及文件名（除去IP）
            String objectName = convertFilePath(filePath);

            // 获取内容类型
            String contentType = getContentType(extension);


            try (InputStream fileStream = new FileInputStream(localFilePath)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(fileStream, file.length(), -1)
                                .contentType(contentType)
                                .build()
                );
            }
            // 获取预签名URL
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(60 * 60 * 24 * 7) // Expiry time for the presigned URL
                            .build()
            );

            return presignedUrl.split("\\?")[0];
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            return null;
        } finally {
            // 删除本地文件
            file.delete();
        }

    }

    public  String convertFilePath(String filePath) {

        int startIdx = filePath.indexOf("\\") + 2;
        if (startIdx >= filePath.length()) {
            // 如果找不到 "\\" 或者 "\\" 后面没有字符，则直接返回原路径
            return filePath;
        }

        String trimmedPath = filePath.substring(startIdx);

        // 去掉 IP 地址部分
        int endIdx = trimmedPath.indexOf("\\");
        if (endIdx == -1) {
            // 如果找不到 "\\"，则直接返回转换后的路径
            return trimmedPath.replace("\\", "/");
        }

        // 获取 IP 地址后面的路径部分
        String pathAfterIP = trimmedPath.substring(endIdx + 1);

        // 替换 "\\" 为 "/"
        String convertedPath = pathAfterIP.replace("\\", "/");

        return convertedPath;
    }


    /*public List<String> uploadBatchToMinIO(List<String> filePaths) {
        String localFilePath = "";
        List<String> uploadedUrls = new ArrayList<>();
        try {
            String bucketName = "bpmpod";

            for (int i = 0; i < filePaths.size(); i++) {
                String filePath = filePaths.get(i);

                // 判断文件是否存在
                File file = new File(filePath);
                if (!file.exists() || !file.isFile()) {
                    uploadedUrls.add("文件不存在或不是一个有效的文件");
                    return uploadedUrls;
                }

                // 获取扩展名和文件名
                String extension = getFileExtension(filePath);
                String fileName = UUID.randomUUID().toString() + extension;

                // 文件路径及文件名（除去IP）
                String objectName = new File(filePath).getName();

                // 获取内容类型
                String contentType = getContentType(extension);

                // 存到本地目录
                String destinationDirectory = destPath; // 目标文件夹路径
                File directory = new File(destinationDirectory);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                localFilePath = destinationDirectory + fileName;
                Files.copy(new FileInputStream(file), Paths.get(localFilePath), StandardCopyOption.REPLACE_EXISTING);

                // 上传到MinIO
                try (InputStream fileStream = new FileInputStream(localFilePath)) {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(objectName)
                                    .stream(fileStream, file.length(), -1)
                                    .contentType(contentType)
                                    .build()
                    );
                }

                // 删除本地文件
                Files.delete(Paths.get(localFilePath));

                // 获取预签名URL
                String presignedUrl = minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(bucketName)
                                .object(objectName)
                                .expiry(60 * 60 * 24 * 7) // Expiry time for the presigned URL
                                .build()
                );
                String ur = presignedUrl.split("\\?")[0];
                uploadedUrls.add(ur);
            }
            return uploadedUrls;
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }*/



    public List<String> uploadBatchToMinIO(List<String> filePaths) {
        List<String> uploadedUrls = new ArrayList<>();
        List<String> localUrls = new ArrayList<>();
        String bucketName = "bpmpod";
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            for (String filePath : filePaths) {
                final String finalFilePath = filePath;
                executor.submit(() -> {
                    String localFilePath = "";
                    File file = new File(finalFilePath);
                    if (!file.exists() || !file.isFile()) {
                        synchronized (uploadedUrls) {
                            uploadedUrls.add("文件不存在或不是一个有效的文件");
                        }
                        return;
                    }

                    // 获取扩展名和文件名
                    String extension = getFileExtension(filePath);
                    String fileName = UUID.randomUUID() + extension;


                    // 文件路径及文件名（除去IP）
                    String objectName = convertFilePath(filePath);

                    // 获取内容类型
                    String contentType = getContentType(extension);

                    // 存到本地目录
                    String destinationDirectory = destPath; // 目标文件夹路径
                    File directory = new File(destinationDirectory);
                    if (!directory.exists()) {
                        directory.mkdirs();
                    }
                    localFilePath = destinationDirectory + fileName;
                    try {
                        Files.copy(new FileInputStream(file), new File(localFilePath).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // 上传到MinIO
                    try (InputStream fileStream = new FileInputStream(localFilePath)) {
                        minioClient.putObject(
                                PutObjectArgs.builder()
                                        .bucket(bucketName)
                                        .object(objectName)
                                        .stream(fileStream, file.length(), -1)
                                        .contentType(contentType)
                                        .build()
                        );

                        // 删除本地文件
                  //      Files.delete(new File(localFilePath).toPath());
                        localUrls.add(localFilePath);
                        // 获取预签名URL
                        String presignedUrl = minioClient.getPresignedObjectUrl(
                                GetPresignedObjectUrlArgs.builder()
                                        .method(Method.GET)
                                        .bucket(bucketName)
                                        .object(objectName)
                                        .expiry(60 * 60 * 24 * 7) // Expiry time for the presigned URL
                                        .build()
                        );
                        String url = presignedUrl.split("\\?")[0];
                        uploadedUrls.add(url);

                    } catch (IOException | NoSuchAlgorithmException | InvalidKeyException | ErrorResponseException |
                             InsufficientDataException | InternalException | InvalidResponseException |
                             ServerException | XmlParserException e) {
                        System.err.println("上传文件到 MinIO 时发生错误: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }

            // 等待所有任务完成
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);

            return uploadedUrls;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("上传过程中发生中断: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            for (int i = 0; i < localUrls.size(); i++) {
                String localFilePath = localUrls.get(i);
                try {
                    Files.delete(new File(localFilePath).toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    public String processFile(String filePath){
        try {
            // 判断文件是否存在
            File file = new File(filePath);
            if (!file.exists()) {
                return "文件不存在";
            }

            // 计算文件的哈希值（使用 MD5 或 SHA-256）
            String fileHash = calculateFileHash(filePath);

            // 获取扩展名和目标文件路径
            String extension = getFileExtension(filePath);
            String fileName = fileHash + extension;
            String destinationDirectory = destPath; // 目标文件夹路径

            // 目标文件路径
            String localFilePath = destinationDirectory + fileName;
            Path destinationPath = Paths.get(localFilePath);

            // 检查文件是否已经存在
            if (Files.exists(destinationPath)) {
                return fileName; // 文件已经存在，直接返回路径
            }

            // 创建目标目录
            File directory = new File(destinationDirectory);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // 将文件复制到目标路径
            try {
                Files.copy(new FileInputStream(file), new File(localFilePath).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e){
                e.printStackTrace();
            }

            return fileName;
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String calculateFileHash(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest(filePath.getBytes());
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String getFileExtension(String filePath) {
        int lastIndexOfDot = filePath.lastIndexOf('.');
        return (lastIndexOfDot == -1) ? "" : filePath.substring(lastIndexOfDot);
    }

    private String getContentType(String extension) {
        switch (extension.toLowerCase()) {
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".bmp":
                return "image/bmp";
            case ".webp":
                return "image/webp";
            default:
                return "application/octet-stream"; // 默认为二进制流
        }
    }

}
