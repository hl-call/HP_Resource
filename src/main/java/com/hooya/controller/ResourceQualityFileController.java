package com.hooya.controller;

import com.hooya.domain.dto.Result;
import com.hooya.domain.dto.TemuOMArt;
import com.hooya.domain.vo.*;
import com.hooya.mapper.cxtrade.BaseMapper;
import com.hooya.mapper.pim.PIMPMMinioImagePathMapper;
import com.hooya.mapper.pim.PIMPMMinioQualityFilePathMapper;
import com.hooya.mapper.pim.PIMQualityFilePathMapper;
import com.hooya.util.MinIOHelper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sf.sevenzipjbinding.*;

import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/16 16:22
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/resQualityFile")
@Slf4j
public class ResourceQualityFileController {

    @Autowired
    public BaseMapper baseMapper;

    public final MinIOHelper minIOHelper;

    public final ExecutorService taskExecutor;

    @Autowired
    PIMPMMinioQualityFilePathMapper pimpmMinioQualityFilePathMapper;

    @Autowired
    PIMQualityFilePathMapper pimQualityFilePathMapper;

    @Value("${destPath}")
    private String destPath;

    // 定义常量
    private static final String BUSINESS_NAME_BTTP = "标贴图片";
    private static final String BUSINESS_NAME_ZS = "证书";

    @PostMapping("/listQualityFile")
    public Result<Object> listQualityFile(@RequestBody ResQueryVo resQueryVo) {
        String cpbh = resQueryVo.getCpbh();
        if (!StringUtils.hasText(cpbh)) {
            return new Result<>(500, null, "cpbh不能为空!");
        }
        List<String> cpbhList = processCpbh(resQueryVo.getCpbh());
        String cpbhQuery = "";
        if(cpbhList==null){
            cpbhQuery =  resQueryVo.getCpbh();
        }
        List<String> countryList = resQueryVo.getArea();
        Set<String> countrySet = new HashSet<>(countryList);
        Iterator<String> iterator = countrySet.iterator();
        while (iterator.hasNext()) {
            String country = iterator.next();
            if (("DE".equals(country) || "ES".equals(country) || "FR".equals(country) || "IT".equals(country)) && !countryList.contains("EU")) {
                countryList.remove(country);
                countryList.add("EU");
            }
            if ("UK".equals(country) && !countryList.contains("GB")) {
                countryList.remove(country);
                countryList.add("GB");
            }
        }
        if (countryList.size() == 0) {
            countryList.add("US");
        }

        List<TemuOMArt> result = new ArrayList<>();
        Set<TemuOMArt> set = new LinkedHashSet<>(result);
        result = new ArrayList<>(set);


        List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathCpbhList = pimpmMinioQualityFilePathMapper.queryByCpbh(cpbhList.get(0));
        List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathBtwjList = pimpmMinioQualityFilePathMapper.queryByBtwj(cpbhList.get(0));
        List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathZsList = pimpmMinioQualityFilePathMapper.queryByZs(cpbhList.get(0));
        TemuOMArt temuOMArtCpbh = new TemuOMArt();
        temuOMArtCpbh.setQualityInfo(pimpmMinioImagePathCpbhList);
        temuOMArtCpbh.setBusniess_name(cpbh);
        result.add(temuOMArtCpbh);

        TemuOMArt temuOMArtBtwj = new TemuOMArt();
        temuOMArtBtwj.setQualityInfo(pimpmMinioImagePathBtwjList);
        temuOMArtBtwj.setBusniess_name(BUSINESS_NAME_BTTP);
        result.add(temuOMArtBtwj);

        TemuOMArt temuOMArtZs = new TemuOMArt();
        temuOMArtZs.setQualityInfo(pimpmMinioImagePathZsList);
        temuOMArtZs.setBusniess_name(BUSINESS_NAME_ZS);
        result.add(temuOMArtZs);


        List<PIMPMMinioQualityFilePathVo> pimNewImagePathCpbhList = pimpmMinioQualityFilePathMapper.queryQualityImgBycpbh(cpbhList.get(0));
        // 根据 groupName 分组
        Map<String, List<PIMPMMinioQualityFilePathVo>> groupedByGroupName = pimNewImagePathCpbhList.stream()
                .collect(Collectors.groupingBy(PIMPMMinioQualityFilePathVo::getGroupName));

        // 将每个分组添加到 temuOMArtCpbh 中
        for (Map.Entry<String, List<PIMPMMinioQualityFilePathVo>> entry : groupedByGroupName.entrySet()) {
            TemuOMArt subTemuOMArt = new TemuOMArt();
            subTemuOMArt.setQualityInfo(entry.getValue());
            subTemuOMArt.setBusniess_name(entry.getKey());
            result.add(subTemuOMArt);
        }


        return new Result<>(200, result, "操作成功!");
    }


    @PostMapping("/listQualityFileNew")
    public Result<Object> listQualityFileNew(@RequestBody ResQueryVo resQueryVo) {
        String cpbh = resQueryVo.getCpbh();
        if (!StringUtils.hasText(cpbh)) {
            return new Result<>(500, null, "cpbh不能为空!");
        }

        List<String> countryList = resQueryVo.getArea();
        Set<String> countrySet = new HashSet<>(countryList);
        Iterator<String> iterator = countrySet.iterator();
        while (iterator.hasNext()) {
            String country = iterator.next();
            if (("DE".equals(country) || "ES".equals(country) || "FR".equals(country) || "IT".equals(country)) && !countryList.contains("EU")) {
                countryList.remove(country);
                countryList.add("EU");
            }
            if ("UK".equals(country) && !countryList.contains("GB")) {
                countryList.remove(country);
                countryList.add("GB");
            }
        }
        if (countryList.size() == 0) {
            countryList.add("US");
        }
        ResQcReportVo resQcReportVo = baseMapper.queryQualityFileByCpbh(cpbh, countryList);
        if (resQcReportVo == null) {
            System.out.println("该产品编号查找不到相关压缩包！");
            return new Result<>(500, null, "该产品编号查找不到相关压缩包!");
        } else {
            String path = resQcReportVo.getPath();
            if (path != null) {
                List<TemuOMArt> temuOMArts =artTaskToQMArtNew(resQcReportVo, cpbh);
                return new Result<>(200, temuOMArts, "操作成功!");
            }
        }
        return new Result<>(500, null, "没有查询到数据!");
    }


    @PostMapping("/listOtherQualityFile")
    public Result<Object> listOtherQualityFile(@RequestBody ResQueryVo resQueryVo) {
        String cpbh = resQueryVo.getCpbh();
        if (!StringUtils.hasText(cpbh)) {
            return new Result<>(500, null, "cpbh不能为空!");
        }
        List<String> countryList = resQueryVo.getArea();
        Set<String> countrySet = new HashSet<>(countryList);
        Iterator<String> iterator = countrySet.iterator();
        while (iterator.hasNext()) {
            String country = iterator.next();
            if (("DE".equals(country) || "ES".equals(country) || "FR".equals(country) || "IT".equals(country)) && !countryList.contains("EU")) {
                countryList.remove(country);
                countryList.add("EU");
            }
            if ("UK".equals(country) && !countryList.contains("GB")) {
                countryList.remove(country);
                countryList.add("GB");
            }
        }
        if (countryList.size() == 0) {
            countryList.add("US");
        }
        List<TemuOMArt> result = new ArrayList<>();
        // 从特定共享磁盘获取文件
        List<PIMQualityFilePathVo> belongingPathByCpbh = pimQualityFilePathMapper.getBelongingPathByCpbh(cpbh);
        if (belongingPathByCpbh.size() > 0) {
            for (int i = 0; i < belongingPathByCpbh.size(); i++) {
                String belongingPath = belongingPathByCpbh.get(i).getBelongingPath();
                List<PIMQualityFilePathVo> belongingPathList = pimQualityFilePathMapper.queryByBelongingPath(cpbh, belongingPath);
                TemuOMArt temuOMArt = new TemuOMArt();
                temuOMArt.setOtherQualityInfo(belongingPathList);
                temuOMArt.setBusniess_name("其他");
                temuOMArt.setFile_path(belongingPath);
                result.add(temuOMArt);
            }
            return new Result<>(200, result, "操作成功!");
        } else {
            return new Result<>(500, null, "没有查询到数据!");

        }
    }


    public List<TemuOMArt> artTaskToQMArt(ResQcReportVo t, String cpbh) {

        List<TemuOMArt> result = new ArrayList<>();
        String downloadUrl = t.getPath();
        String country = t.getCountry();
        String fileName = getFileNameFromUrl(downloadUrl);
        String localFilePath = destPath + fileName;
        int end = fileName.indexOf(".");
        String filePath = fileName.substring(0, end);
        String extractDir = destPath + filePath;

        try {
            Set<TemuOMArt> set = new LinkedHashSet<>(result);
            result = new ArrayList<>(set);

//
//            // Step 1: 下载rar文件
//            //换成刷数据的话，就不用下载了直接取某段
////            downloadFile(downloadUrl, localFilePath);
//
//            // Step 2: 保存md5值,比对md5值，若一致则从数据库直接查文件，若不一致则上传到minio，并保存到数据库
//            String md5 = getMD5Checksum(localFilePath).trim();
//            PIMPMMinioQualityFilePathVo pimpmMinioImagePath = pimpmMinioQualityFilePathMapper.queryRarMd5(cpbh,downloadUrl);
//            boolean md5Result = false;
//            if(null!=pimpmMinioImagePath){
//               String zipMd5 = pimpmMinioImagePath.getZipMd5().trim();
//               md5Result = md5.equalsIgnoreCase(zipMd5);
//            }
//
//            if (md5Result) {
            log.info("md5一致，从数据库中获取文件");
            //从这里开始
            List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathCpbhList = pimpmMinioQualityFilePathMapper.queryByCpbh(cpbh);
            List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathBtwjList = pimpmMinioQualityFilePathMapper.queryByBtwj(cpbh);
            List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathZsList = pimpmMinioQualityFilePathMapper.queryByZs(cpbh);
            TemuOMArt temuOMArtCpbh = new TemuOMArt();
            temuOMArtCpbh.setQualityInfo(pimpmMinioImagePathCpbhList);
            temuOMArtCpbh.setBusniess_name(cpbh);
            result.add(temuOMArtCpbh);

            TemuOMArt temuOMArtBtwj = new TemuOMArt();
            temuOMArtBtwj.setQualityInfo(pimpmMinioImagePathBtwjList);
            temuOMArtBtwj.setBusniess_name(BUSINESS_NAME_BTTP);
            result.add(temuOMArtBtwj);

            TemuOMArt temuOMArtZs = new TemuOMArt();
            temuOMArtZs.setQualityInfo(pimpmMinioImagePathZsList);
            temuOMArtZs.setBusniess_name(BUSINESS_NAME_ZS);
            result.add(temuOMArtZs);
            //到这里结束
//            }
//            //直接去掉
//            else {
//                log.info("md5不一致，重新上传并获取文件");
//                // Step 2: 解压rar文件
//                extractRar(localFilePath, extractDir);
//
//                // Step 3: 从特定目录获取文件
//                List<String> filePathCpbh = processExtractedFiles(extractDir, cpbh);
//                if(filePathCpbh.size()>0){
//                    List<TemuOMArt> temuOMArtsCpbh = uploadToMinio(filePathCpbh, cpbh,cpbh,downloadUrl,md5,country);
//                    result.addAll(temuOMArtsCpbh);
//                }else {
//                    TemuOMArt temuOMArtResult = new TemuOMArt();
//                    temuOMArtResult.setBusniess_name(cpbh);
//                    result.add(temuOMArtResult);
//                }
//
//
//                List<String> filePathBtwj = processExtractedFiles(extractDir, BUSINESS_NAME_BTTP);
//                if(filePathBtwj.size()>0){
//                    List<TemuOMArt> temuOMArtsBtwj = uploadToMinio(filePathBtwj, cpbh,BUSINESS_NAME_BTTP,downloadUrl,md5,country);
//                    result.addAll(temuOMArtsBtwj);
//                } else {
//                    TemuOMArt temuOMArtResult = new TemuOMArt();
//                    temuOMArtResult.setBusniess_name(BUSINESS_NAME_BTTP);
//                    result.add(temuOMArtResult);
//                }
//
//
//                List<String> filePathZs = processExtractedFiles(extractDir, BUSINESS_NAME_ZS);
//                if(filePathZs.size()>0){
//                    List<TemuOMArt> temuOMArtsZs = uploadToMinio(filePathZs, cpbh,BUSINESS_NAME_ZS,downloadUrl,md5,country);
//                    result.addAll(temuOMArtsZs);
//                }else {
//                    TemuOMArt temuOMArtResult = new TemuOMArt();
//                    temuOMArtResult.setBusniess_name(BUSINESS_NAME_ZS);
//                    result.add(temuOMArtResult);
//                }
//            }
//            //结束
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

    /**
     * 手动刷质检报告压缩包
     *
     * @param t
     * @param cpbh
     * @return
     */
    public List<TemuOMArt> artTaskToQMArtNew(ResQcReportVo t, String cpbh) {

        List<TemuOMArt> result = new ArrayList<>();
        String downloadUrl = t.getPath();
        String country = t.getCountry();
        String fileName = getFileNameFromUrl(downloadUrl);
        String localFilePath = destPath + fileName;
        int end = fileName.indexOf(".");
        String filePath = fileName.substring(0, end);
        String extractDir = destPath + filePath;

        try {
            Set<TemuOMArt> set = new LinkedHashSet<>(result);
            result = new ArrayList<>(set);


            // Step 1: 下载rar文件
            //换成刷数据的话，就不用下载了直接取某段
            downloadFile(downloadUrl, localFilePath);

            // Step 2: 保存md5值,比对md5值，若一致则从数据库直接查文件，若不一致则上传到minio，并保存到数据库
            String md5 = getMD5Checksum(localFilePath).trim();
            PIMPMMinioQualityFilePathVo pimpmMinioImagePath = pimpmMinioQualityFilePathMapper.queryRarMd5(cpbh, downloadUrl);
            boolean md5Result = false;
            if (null != pimpmMinioImagePath) {
                String zipMd5 = pimpmMinioImagePath.getZipMd5().trim();
                md5Result = md5.equalsIgnoreCase(zipMd5);
            }

            if (md5Result) {
                log.info("md5一致，从数据库中获取文件");
                //从这里开始
                List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathCpbhList = pimpmMinioQualityFilePathMapper.queryByCpbh(cpbh);
                List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathBtwjList = pimpmMinioQualityFilePathMapper.queryByBtwj(cpbh);
                List<PIMPMMinioQualityFilePathVo> pimpmMinioImagePathZsList = pimpmMinioQualityFilePathMapper.queryByZs(cpbh);
                TemuOMArt temuOMArtCpbh = new TemuOMArt();
                temuOMArtCpbh.setQualityInfo(pimpmMinioImagePathCpbhList);
                temuOMArtCpbh.setBusniess_name(cpbh);
                result.add(temuOMArtCpbh);

                TemuOMArt temuOMArtBtwj = new TemuOMArt();
                temuOMArtBtwj.setQualityInfo(pimpmMinioImagePathBtwjList);
                temuOMArtBtwj.setBusniess_name(BUSINESS_NAME_BTTP);
                result.add(temuOMArtBtwj);

                TemuOMArt temuOMArtZs = new TemuOMArt();
                temuOMArtZs.setQualityInfo(pimpmMinioImagePathZsList);
                temuOMArtZs.setBusniess_name(BUSINESS_NAME_ZS);
                result.add(temuOMArtZs);
            }
            //直接去掉
            else {
                log.info("md5不一致，重新上传并获取文件");
                // Step 2: 解压rar文件
                extractRar(localFilePath, extractDir);

                // Step 3: 从特定目录获取文件
                List<String> filePathCpbh = processExtractedFiles(extractDir, cpbh);
                if (filePathCpbh.size() > 0) {
                    List<TemuOMArt> temuOMArtsCpbh = uploadToMinio(filePathCpbh, cpbh, cpbh, downloadUrl, md5, country);
                    result.addAll(temuOMArtsCpbh);
                } else {
                    TemuOMArt temuOMArtResult = new TemuOMArt();
                    temuOMArtResult.setBusniess_name(cpbh);
                    result.add(temuOMArtResult);
                }


                List<String> filePathBtwj = processExtractedFiles(extractDir, BUSINESS_NAME_BTTP);
                if (filePathBtwj.size() > 0) {
                    List<TemuOMArt> temuOMArtsBtwj = uploadToMinio(filePathBtwj, cpbh, BUSINESS_NAME_BTTP, downloadUrl, md5, country);
                    result.addAll(temuOMArtsBtwj);
                } else {
                    TemuOMArt temuOMArtResult = new TemuOMArt();
                    temuOMArtResult.setBusniess_name(BUSINESS_NAME_BTTP);
                    result.add(temuOMArtResult);
                }


                List<String> filePathZs = processExtractedFiles(extractDir, BUSINESS_NAME_ZS);
                if (filePathZs.size() > 0) {
                    List<TemuOMArt> temuOMArtsZs = uploadToMinio(filePathZs, cpbh, BUSINESS_NAME_ZS, downloadUrl, md5, country);
                    result.addAll(temuOMArtsZs);
                } else {
                    TemuOMArt temuOMArtResult = new TemuOMArt();
                    temuOMArtResult.setBusniess_name(BUSINESS_NAME_ZS);
                    result.add(temuOMArtResult);
                }
            }
            //结束
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

    public List<TemuOMArt> uploadToMinio(List<String> filePaths, String cpbh, String folderName, String zipUrl, String zipMd5, String country) {
        // 创建异步任务
        List<CompletableFuture<TemuOMArt>> futures = new ArrayList<>();
        TemuOMArt temuOMArtResult = new TemuOMArt();
        CompletableFuture<TemuOMArt> future = CompletableFuture.supplyAsync(() -> {
            List<PIMPMMinioQualityFilePathVo> uploadedUrls = filePaths.stream()
                    .map(imagePathBase -> {
                        try {
                            int lastSlashIndex = imagePathBase.lastIndexOf('\\');
                            String realName = null;
                            if (lastSlashIndex != -1) {
                                realName = imagePathBase.substring(lastSlashIndex + 1);
                            }
                            PIMPMMinioQualityFilePathVo pimpmMinioImagePath = pimpmMinioQualityFilePathMapper.queryQualityMinioPath(cpbh, realName, folderName);
                            if (null != pimpmMinioImagePath) {
                                pimpmMinioImagePath.setZipUrl(zipUrl.trim());
                                pimpmMinioImagePath.setZipMd5(zipMd5.trim());
                                pimpmMinioQualityFilePathMapper.updatePpQualityFile(pimpmMinioImagePath);
                                return pimpmMinioImagePath;
                            } else {
                                String minioPathNew = minIOHelper.uploadToMinIO(imagePathBase, imagePathBase);
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


    private List<String> processExtractedFiles(String extractDir, String name) {
        List<String> paths = new ArrayList<>();
        Path dirPath = Paths.get(extractDir);
        try (Stream<Path> stream = Files.walk(dirPath)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> path.endsWith(name))
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
                        paths.add(extractDir + "\\" + relativePath);
                        System.out.println("Processing file: " + extractDir + "\\" + relativePath);
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
     *
     * @param filePath 文件路径
     * @return 文件的 MD5 校验和字符串
     * @throws IOException              如果文件读取过程中发生错误
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


    private List<String> processCpbh(String cpbh) {
        if (!StringUtils.hasText(cpbh)) {
            return null;
        }
        String[] split = cpbh.split(",");
        List<String> cpbhList = Arrays.asList(split);
        return cpbhList.size() > 1 ? cpbhList : null;
    }
}
