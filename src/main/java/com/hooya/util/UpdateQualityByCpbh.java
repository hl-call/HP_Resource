package com.hooya.util;

import com.hooya.domain.dto.TemuOMArt;
import com.hooya.domain.vo.PIMPMMinioQualityFilePathVo;
import com.hooya.domain.vo.ResQcReportVo;
import com.hooya.mapper.cxtrade.BaseMapper;
import com.hooya.mapper.pim.PIMPMMinioImagePathMapper;
import com.hooya.mapper.pim.PIMPMMinioQualityFilePathMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/19 14:03
 **/
@Component
@Slf4j
public class UpdateQualityByCpbh {

    @Autowired
    public  BaseMapper baseMapper;
    @Autowired
    public  MinIOHelper minIOHelper;
    @Autowired
    public  ExecutorService taskExecutor;
    @Autowired
    PIMPMMinioQualityFilePathMapper pimpmMinioQualityFilePathMapper;

    @Value("${destPath}")
    private String destPath;

    // 定义常量
    private static final String BUSINESS_NAME_BTTP = "标贴图片";
    private static final String BUSINESS_NAME_ZS = "证书";



    public List<TemuOMArt> processCpbh(String cpbh) {
        List<String> countryList = new ArrayList<>();
        countryList.add("US");
        ResQcReportVo resQcReportVo = baseMapper.queryQualityFileByCpbh(cpbh, countryList);
        List<TemuOMArt> temuOMArts = null;
        if(resQcReportVo == null){
            System.out.println("该产品编号查找不到相关压缩包！");
        } else {
            String path = resQcReportVo.getPath();
            if (path != null) {
                temuOMArts =artTaskToQMArt(resQcReportVo, cpbh);
            }
        }
        return temuOMArts;
    }
    public List<TemuOMArt> artTaskToQMArt(ResQcReportVo t,String cpbh) {

        List<TemuOMArt> result = new ArrayList<>();
        String downloadUrl = t.getPath();
        String country = t.getCountry();
        String fileName = getFileNameFromUrl(downloadUrl);
        String localFilePath = destPath+fileName;
        int end = fileName.indexOf(".");
        String filePath = fileName.substring(0, end);
        String extractDir = destPath+filePath;

        try {
            Set<TemuOMArt> set = new LinkedHashSet<>(result);
            result = new ArrayList<>(set);


            // Step 1: 下载rar文件
            downloadFile(downloadUrl, localFilePath);

            // Step 2: 保存md5值,比对md5值，若一致则从数据库直接查文件，若不一致则上传到minio，并保存到数据库
            String md5 = getMD5Checksum(localFilePath).trim();
            PIMPMMinioQualityFilePathVo pimpmMinioImagePath = pimpmMinioQualityFilePathMapper.queryRarMd5(cpbh,downloadUrl);
            boolean md5Result = false;
            if(null!=pimpmMinioImagePath){
                String zipMd5 = pimpmMinioImagePath.getZipMd5().trim();
                md5Result = md5.equalsIgnoreCase(zipMd5);
            }

            if (md5Result) {
                log.info("md5一致，不做更改");
            } else {
                log.info("md5不一致，重新上传并获取文件");
                // Step 2: 解压rar文件
                extractRar(localFilePath, extractDir);

                // Step 3: 从特定目录获取文件
                List<String> filePathCpbh = processExtractedFiles(extractDir, cpbh);
                if(filePathCpbh.size()>0){
                    List<TemuOMArt> temuOMArtsCpbh = uploadToMinio(filePathCpbh, cpbh,cpbh,downloadUrl,md5,country);
                    result.addAll(temuOMArtsCpbh);
                }else {
                    TemuOMArt temuOMArtResult = new TemuOMArt();
                    temuOMArtResult.setBusniess_name(cpbh);
                    result.add(temuOMArtResult);
                }


                List<String> filePathBtwj = processExtractedFiles(extractDir, BUSINESS_NAME_BTTP);
                if(filePathBtwj.size()>0){
                    List<TemuOMArt> temuOMArtsBtwj = uploadToMinio(filePathBtwj, cpbh,BUSINESS_NAME_BTTP,downloadUrl,md5,country);
                    result.addAll(temuOMArtsBtwj);
                } else {
                    TemuOMArt temuOMArtResult = new TemuOMArt();
                    temuOMArtResult.setBusniess_name(BUSINESS_NAME_BTTP);
                    result.add(temuOMArtResult);
                }


                List<String> filePathZs = processExtractedFiles(extractDir, BUSINESS_NAME_ZS);
                if(filePathZs.size()>0){
                    List<TemuOMArt> temuOMArtsZs = uploadToMinio(filePathZs, cpbh,BUSINESS_NAME_ZS,downloadUrl,md5,country);
                    result.addAll(temuOMArtsZs);
                }else {
                    TemuOMArt temuOMArtResult = new TemuOMArt();
                    temuOMArtResult.setBusniess_name(BUSINESS_NAME_ZS);
                    result.add(temuOMArtResult);
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Step 4: 删除rar文件和解压目录
            deleteDirectory(Paths.get(localFilePath));
            deleteDirectory(Paths.get(extractDir));
        }
        return result;
    }



    @SneakyThrows
    public static String getFileNameFromUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            String path = uri.getPath();
            int lastSlashIndex = path.lastIndexOf('/');
            if (lastSlashIndex != -1) {
                return path.substring(lastSlashIndex + 1);
            }
        } catch (URISyntaxException e) {
            System.err.println("Invalid URL: " + urlString + " - " + e.getMessage());
        }
        return null;
    }

    public List<TemuOMArt> uploadToMinio(List<String> filePaths,String cpbh,String folderName,String zipUrl,String zipMd5,String country) {
        // 创建异步任务
        List<CompletableFuture<TemuOMArt>> futures = new ArrayList<>();
        TemuOMArt temuOMArtResult = new TemuOMArt();
       // PIMPMMinioQualityFilePathVo pimpmMinioImagePath = pimpmMinioImagePathMapper.queryQualityMinioPath(cpbh,folderName);

        CompletableFuture<TemuOMArt> future = CompletableFuture.supplyAsync(() -> {
            List<PIMPMMinioQualityFilePathVo> uploadedUrls = filePaths.stream()
                    .map(imagePathBase -> {
                        try {
                            int lastSlashIndex = imagePathBase.lastIndexOf('\\');
                            String realName = null;
                            if (lastSlashIndex != -1) {
                                realName = imagePathBase.substring(lastSlashIndex + 1);
                            }
                            PIMPMMinioQualityFilePathVo pimpmMinioImagePath = pimpmMinioQualityFilePathMapper.queryQualityMinioPath(cpbh,realName,folderName);
                            if(null!=pimpmMinioImagePath){
                                pimpmMinioImagePath.setZipUrl(zipUrl.trim());
                                pimpmMinioImagePath.setZipMd5(zipMd5.trim());
                                pimpmMinioQualityFilePathMapper.updatePpQualityFile(pimpmMinioImagePath);
                                return pimpmMinioImagePath;
                            } else {
                                String minioPathNew = minIOHelper.uploadToMinIO(imagePathBase,imagePathBase);
                                PIMPMMinioQualityFilePathVo pimpmMinioQualityFilePathVo = new PIMPMMinioQualityFilePathVo();
                                pimpmMinioQualityFilePathVo.setSku(cpbh.trim());
                                pimpmMinioQualityFilePathVo.setRealName(realName.trim());
                                pimpmMinioQualityFilePathVo.setFolderName(folderName.trim());
                                pimpmMinioQualityFilePathVo.setMinioPath(minioPathNew.trim());
                                pimpmMinioQualityFilePathVo.setZipUrl(zipUrl.trim());
                                pimpmMinioQualityFilePathVo.setZipMd5(zipMd5.trim());
                                pimpmMinioQualityFilePathVo.setCountry(country);
                                //新增一条的id
                                pimpmMinioQualityFilePathMapper.insertPpQualityFile(pimpmMinioQualityFilePathVo);
                                Long id = pimpmMinioQualityFilePathVo.getId();
                                pimpmMinioQualityFilePathVo.setId(pimpmMinioQualityFilePathVo.getId());
                                return pimpmMinioQualityFilePathVo;
                            }
                        } catch (Exception e) {
                            // 处理异常情况
                            log.error("Failed to upload image: {}", imagePathBase, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 将上传的URL添加到原始对象的 PictureUrl 中
            temuOMArtResult.getQualityInfo().addAll(uploadedUrls);
            temuOMArtResult.setBusniess_name(folderName);
            return temuOMArtResult;
        }, taskExecutor);
        futures.add(future);
        List<TemuOMArt> updatedResults = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
        return updatedResults;
    }
    @SneakyThrows
    private static void downloadFile(String urlStr, String filePath) throws IOException, NoSuchAlgorithmException {
        URL url = new URL(urlStr);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream inputStream = httpConn.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(filePath)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } else {
            throw new RuntimeException("Failed to download file: HTTP error code : " + responseCode);
        }
    }

    @SneakyThrows
    private static void extractRar(String rarFilePath, String destDirectory) throws IOException, SevenZipException {
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;
        try {
            // 第一个参数是需要解压的压缩包路径，第二个参数参考JdkAPI文档的RandomAccessFile
            randomAccessFile = new RandomAccessFile(rarFilePath, "r");
            inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile));

            ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();



            for (final ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
                final int[] hash = new int[]{0};
                if (!item.isFolder()) {
                    ExtractOperationResult result;
                    final long[] sizeArray = new long[1];

                    String dest = destDirectory + File.separator;
                    File outFile = new File(dest + item.getPath());
                    File parent = outFile.getParentFile();
                    if ((!parent.exists()) && (!parent.mkdirs())) {
                        continue;
                    }

                    result = item.extractSlow(data -> {
                        try (FileOutputStream fos = new FileOutputStream(outFile, true)) {
                            IOUtils.write(data, fos);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return -1; // 返回-1表示写入失败
                        }
                        hash[0] ^= Arrays.hashCode(data); // Consume data
                        sizeArray[0] += data.length;
                        return data.length; // Return amount of consumed
                    });

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (inArchive != null && randomAccessFile != null) {
                    inArchive.close();
                    randomAccessFile.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    private  List<String> processExtractedFiles(String extractDir,String name) {
        List<String> paths = new ArrayList<>();
        Path dirPath = Paths.get(extractDir);
        try (Stream<Path> stream = Files.walk(dirPath)) {
            stream.filter(Files::isDirectory)
                    .filter(path ->  path.endsWith(name))
                    .flatMap(directory -> {
                        try {
                            return Files.walk(directory);
                        } catch (IOException e) {
                            System.err.println("Error walking directory: " + directory + " - " + e.getMessage());
                            return Stream.empty();
                        }
                    })
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relativePath = dirPath.relativize(file).toString();
                        paths.add(extractDir+"\\"+ relativePath);
                        System.out.println("Processing file: " +extractDir+"\\"+ relativePath);
                        // 在这里可以添加对文件的进一步处理逻辑
                    });
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
        }
        return paths;
    }

    public static void deleteDirectory(Path path) {
        if (path == null) {
            System.err.println("Path is null, cannot delete.");
            return;
        }

        if (!Files.exists(path)) {
            System.err.println("Path does not exist: " + path);
            return;
        }

        try {
            if (Files.isDirectory(path)) {
                Files.walk(path)
                        .sorted((p1, p2) -> -p1.compareTo(p2)) // Sort in reverse order to delete children first
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (file.delete()) {
                                System.out.println("Deleted: " + file.getAbsolutePath());
                            } else {
                                System.err.println("Failed to delete: " + file.getAbsolutePath());
                            }
                        });
            } else if (Files.isRegularFile(path)) {
                if (Files.deleteIfExists(path)) {
                    System.out.println("Deleted file: " + path);
                } else {
                    System.err.println("Failed to delete file: " + path);
                }
            }
        } catch (IOException e) {
            System.err.println("Error deleting path: " + path + " - " + e.getMessage());
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
