package com.hooya.util;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/18 15:12
 **/
public class test {

    public static void main(String[] args) {
        String file1Path = "E:\\2024067081.rar";
        String file2Path = "E:\\2024067082.rar";


/*        try {
            String md5File1 = getMD5Checksum(file1Path);
            System.out.println("md5File1："+md5File1);
            String md5File2 = getMD5Checksum(file2Path);
            System.out.println("md5File2："+md5File2);

            boolean areEqual = md5File1.equals(md5File2);
            System.out.println(areEqual);
            if (areEqual) {
                System.out.println("The MD5 checksums of the two files are identical.");
            } else {
                System.out.println("The MD5 checksums of the two files are different.");
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }*/
        String cpbh = "HW65973BK/CF/WT-L/M";
        String[] skus = cpbh.split("[^A-Za-z0-9-]+");

        for (int i = 0; i < skus.length; i++) {
            System.out.println(skus[i]);
        }

    }

    /**
     * 计算文件的 MD5 校验和
     * @param filePath 文件路径
     * @return 文件的 MD5 校验和字符串
     * @throws IOException 如果文件读取过程中发生错误
     * @throws NoSuchAlgorithmException 如果 MD5 算法不可用
     */
    public static String getMD5Checksum(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(filePath);
             DigestInputStream dis = new DigestInputStream(fis, md)) {
            byte[] buffer = new byte[8192]; // 8 KB buffer
            int bytesRead;
            while ((bytesRead = dis.read(buffer)) > 0) {
                // 读取文件内容以更新 MD5 校验和
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
