package com.hooya.controller;

import com.hooya.domain.vo.PIMQualityFilePathVo;
import com.hooya.mapper.pim.PIMQualityFilePathMapper;
import com.hooya.util.MinIOHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/18 14:34
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/customizedQualityFile")
@Slf4j
public class CustomizedQualityFileController {
    @Autowired
    PIMQualityFilePathMapper pimQualityFilePathMapper;

    public final MinIOHelper minIOHelper;

    @Value("${destPath}")
    private String destPath;
    @PostMapping("/getQualityFile")
    public void getQualityFile(String folderPath) {

        // 创建文件对象
        File folder = new File(folderPath);

        // 检查文件夹是否存在且是一个目录
        if (folder.exists() && folder.isDirectory()) {
            // 遍历文件夹下的所有文件和子文件夹
            scanImageFiles(folder,folderPath);
        } else {
            System.err.println("指定的路径不是一个有效的文件夹: " + folderPath);
        }
    }

    /**
     * 递归遍历文件夹下的所有文件和子文件夹
     *
     * @param folder 文件夹对象
     */
    private void scanFiles(File folder, String belongingPath) {
        // 获取文件夹下的所有文件和子文件夹
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 如果是子文件夹，递归遍历
                    scanFiles(file,belongingPath);
                } else {
                    // 如果是文件，检查文件扩展名是否为 .pdf 或 .doc 或 .docx
                    String fileName = file.getName().toLowerCase();
                    if (fileName.endsWith(".pdf") || fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                        // 获取上一级文件夹的名字
                        String parentFolderName = file.getParentFile().getName();
                        // 提取上一级文件夹名中的所有大写英文加数字部分
                        List<String> trackingLabels = extractTrackingLabels(parentFolderName);
                        if (!trackingLabels.isEmpty()) {
                            // 打印文件路径、跟踪标签及其上一级文件夹的名字
                            System.out.println("File Path: " + file.getAbsolutePath());
                            StringBuilder trackingLabelString = new StringBuilder();
                            for (String trackingLabel : trackingLabels) {
                                System.out.println("Tracking Label: " + trackingLabel);
                                trackingLabelString.append(trackingLabel+" ");
                            }
                            String md5Value = null;
                            try {
                                md5Value = calculateMD5(file);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } catch (NoSuchAlgorithmException e) {
                                throw new RuntimeException(e);
                            }
                            System.out.println("File Name: " + file.getName());
                            System.out.println("Parent Folder Name: " + parentFolderName);
                            System.out.println("Belonging Path: " + belongingPath);
                            System.out.println("File Md5: " + md5Value);
                            System.out.println("----------------------------------------");

                            // 上传minio
                            String minioPath = getMinioPath(file.getAbsolutePath());

                            // 入库 pim_quality_file_path 表
                            Date date= new Date();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                            String sharePath = file.getAbsolutePath();
                            String sku = trackingLabelString.toString();
                            PIMQualityFilePathVo pimQualityFilePathVo = new PIMQualityFilePathVo();
                            pimQualityFilePathVo.setSharePath(sharePath);
                            pimQualityFilePathVo.setFileName(file.getName());
                            pimQualityFilePathVo.setSku(sku);
                            pimQualityFilePathVo.setBelongingPath(belongingPath);
                            pimQualityFilePathVo.setCreateTime(sdf.format(date));
                            pimQualityFilePathVo.setMinioPath(minioPath);
                            pimQualityFilePathVo.setFileMd5(md5Value);

                            PIMQualityFilePathVo pimQualityFilePath = pimQualityFilePathMapper.qeuryQualityFile(sku, file.getName(), belongingPath);
                            // 提前检查 pimQualityFilePathVo 是否为 null
                            if (pimQualityFilePathVo == null) {
                                log.warn("pimQualityFilePathVo is null, cannot insert or update quality file");
                                return;
                            }

                            if (pimQualityFilePath != null && md5Value != null) {
                                if (!md5Value.equals(pimQualityFilePath.getFileMd5())) {
                                    try {
                                        pimQualityFilePathMapper.updateQualityFile(pimQualityFilePathVo);
                                    } catch (Exception e) {
                                        // 记录日志并处理异常，考虑添加重试机制或其他处理方式
                                        log.error("Failed to update quality file", e);
                                        // 可以在此处添加重试逻辑或其他处理措施
                                    }
                                }
                            } else {
                                try {
                                    pimQualityFilePathMapper.insertQualityFile(pimQualityFilePathVo);
                                } catch (Exception e) {
                                    // 记录日志并处理异常，考虑添加重试机制或其他处理方式
                                    log.error("Failed to insert quality file", e);
                                }
                            }
                        } else {
                            System.err.println("未找到有效的跟踪标签: " + parentFolderName);
                        }
                    }
                }
            }
        }
    }

    public void scanImageFiles(File folder, String belongingPath) {
        // 获取文件夹下的所有文件和子文件夹
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 如果是子文件夹，递归遍历
                    scanImageFiles(file, belongingPath);
                } else {
                    // 如果是文件，检查文件扩展名是否为图片格式
                    String fileName = file.getName().toLowerCase();
                    if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".gif")) {
                        // 获取上一级文件夹的名字
                        String parentFolderName = file.getParentFile().getName();
                        // 提取上一级文件夹名中的所有大写英文加数字部分
                        List<String> trackingLabels = extractTrackingLabels(parentFolderName);
                        if (!trackingLabels.isEmpty()) {
                            // 打印文件路径、跟踪标签及其上一级文件夹的名字
                            System.out.println("File Path: " + file.getAbsolutePath());
                            StringBuilder trackingLabelString = new StringBuilder();
                            for (String trackingLabel : trackingLabels) {
                                System.out.println("Tracking Label: " + trackingLabel);
                                trackingLabelString.append(trackingLabel + " ");
                            }
                            String md5Value = null;
                            try {
                                md5Value = calculateMD5(file);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } catch (NoSuchAlgorithmException e) {
                                throw new RuntimeException(e);
                            }
                            System.out.println("File Name: " + file.getName());
                            System.out.println("Parent Folder Name: " + parentFolderName);
                            System.out.println("Belonging Path: " + belongingPath);
                            System.out.println("File Md5: " + md5Value);
                            System.out.println("----------------------------------------");

                            // 上传minio
                            String minioPath = getMinioPath(file.getAbsolutePath());

                            // 入库 pim_quality_file_path 表
                            Date date = new Date();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                            String sharePath = file.getAbsolutePath();
                            String sku = trackingLabelString.toString();
                            PIMQualityFilePathVo pimQualityFilePathVo = new PIMQualityFilePathVo();
                            pimQualityFilePathVo.setSharePath(sharePath);
                            pimQualityFilePathVo.setFileName(file.getName());
                            pimQualityFilePathVo.setSku(sku);
                            pimQualityFilePathVo.setBelongingPath(belongingPath);
                            pimQualityFilePathVo.setCreateTime(sdf.format(date));
                            pimQualityFilePathVo.setMinioPath(minioPath);
                            pimQualityFilePathVo.setFileMd5(md5Value);
                            pimQualityFilePathVo.setCountry("US");

                            PIMQualityFilePathVo pimQualityFilePath = pimQualityFilePathMapper.qeuryQualityFile(sku, file.getName(), belongingPath);
                            // 提前检查 pimQualityFilePathVo 是否为 null
                            if (pimQualityFilePathVo == null) {
                                log.warn("pimQualityFilePathVo is null, cannot insert or update quality file");
                                return;
                            }

                            if (pimQualityFilePath != null && md5Value != null) {
                                if (!md5Value.equals(pimQualityFilePath.getFileMd5())) {
                                    try {
                                        pimQualityFilePathMapper.updateQualityFile(pimQualityFilePathVo);
                                    } catch (Exception e) {
                                        // 记录日志并处理异常，考虑添加重试机制或其他处理方式
                                        log.error("Failed to update quality file", e);
                                        // 可以在此处添加重试逻辑或其他处理措施
                                    }
                                }
                            } else {
                                try {
                                    pimQualityFilePathMapper.insertQualityFile(pimQualityFilePathVo);
                                } catch (Exception e) {
                                    // 记录日志并处理异常，考虑添加重试机制或其他处理方式
                                    log.error("Failed to insert quality file", e);
                                }
                            }
                        } else {
                            System.err.println("未找到有效的跟踪标签: " + parentFolderName);
                        }
                    }
                }
            }
        }
    }


    private String calculateMD5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (DigestInputStream dis = new DigestInputStream(new FileInputStream(file), md)) {
            while (dis.read() != -1) {
                // 读取文件内容以计算 MD5
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public String getMinioPath(String imagePathBase){
        String localFilePath = null;
        String minioPath = null;
        try {
            String nativeFileName = minIOHelper.processFile(imagePathBase);
            localFilePath = destPath + nativeFileName;
            minioPath = minIOHelper.uploadToMinIO(imagePathBase,localFilePath);
        } catch (Exception e) {
            // 处理异常情况
            log.error("Failed to upload image: {}", imagePathBase, e);
            return null;
        } finally {
            // 删除本地文件
            try {
                Files.delete(Paths.get(localFilePath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        return minioPath;
    }

    /**
     * 从文件夹名中提取所有跟踪标签
     *
     * @param folderName 文件夹名
     * @return 提取的所有跟踪标签列表
     */
    private static List<String> extractTrackingLabels(String folderName) {
        List<String> labels = new ArrayList<>();
        // 使用正则表达式匹配文件夹名中的所有大写英文加数字部分
        Pattern pattern = Pattern.compile("[A-Z]+\\d+");
        Matcher matcher = pattern.matcher(folderName);

        while (matcher.find()) {
            labels.add(matcher.group());
        }

        return labels;
    }
}
