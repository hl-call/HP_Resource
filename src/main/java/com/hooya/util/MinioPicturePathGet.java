package com.hooya.util;

import com.hooya.domain.dto.Result;
import com.hooya.domain.dto.TemuOMArt;
import com.hooya.domain.vo.*;
import com.hooya.mapper.cxtrade.BaseMapper;
import com.hooya.mapper.pim.PIMCpbhImageTypeDimensionMapper;
import com.hooya.mapper.pim.PIMPMMinioImagePathMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * @AUTHOR majiang
 * @DATE 2025/1/16 14:32
 **/
@Component
@Slf4j
@RequiredArgsConstructor
public class MinioPicturePathGet {

    public final BaseMapper baseMapper;

    public final MinIOHelper minIOHelper;

    public final ExecutorService taskExecutor;

    @Autowired
    PIMPMMinioImagePathMapper pimpmMinioImagePathMapper;
    @Autowired
    PIMCpbhImageTypeDimensionMapper pimCpbhImageTypeDimensionMapper;

    @Value("${serverUrl}")
    private String serverUrl;

    @Value("${destPath}")
    private String destPath;

    public void listPictures(String cpbh) {
        System.out.println("当前cpbh为："+cpbh);
        List<ResPictureVo> resPictureVos = baseMapper.queryResourcePictureByCpbh(null,cpbh,null);

        for (ResPictureVo resPictureVo : resPictureVos) {
            List<TemuOMArt> temuOMArts = artTaskToQMArt(resPictureVo);
        }
    }






    private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".mp4"};

    public void getAllFilesInDirectory(String directory, List<String> filePaths, List<String> folderNames) {
        // 路径规范化和验证
        File dir = new File(normalizePath(directory));

        if (dir.isFile()) {
            // 如果路径指向一个文件，返回文件对象
            filePaths.add(directory);
        } else if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory path: " + directory);
        }  else {

            // 获取当前文件夹中的所有图片文件路径（不处理子文件夹）
            File[] imageFiles = listImageFiles(dir);

            if (imageFiles != null) {
                for (File file : imageFiles) {
                    if (file.isFile()) {
                        filePaths.add(file.getAbsolutePath());
                    }
                }
            }

            // 获取当前文件夹中的所有子文件夹
            try (DirectoryStream<Path> subdirectories = Files.newDirectoryStream(dir.toPath(), "*")) {
                for (Path subdirectory : subdirectories) {
                    if (Files.isDirectory(subdirectory)) {
                        String[] split = directory.split("\\\\");
                        int size = split.length;
                        if (size > 8) {
                            getAllFilesInDirectory(subdirectory.toString(), filePaths, folderNames);
                        } else {
                            for (String folderName : folderNames) {
                                if (subdirectory.getFileName().toString().startsWith(folderName)) {
                                    getChildFilesInDirectory(subdirectory.toString(), filePaths);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // 记录异常信息
                System.err.println("Error accessing directory: " + directory);
                e.printStackTrace();
            }
        }
    }

    public List<TemuOMArt> artTaskToQMArt(ResPictureVo t) {

        List<TemuOMArt> result = new ArrayList<>();
        TemuOMArt item = new TemuOMArt();
        item.setCpbh(t.getSku());
        item.setCountry(t.getCountry());

        switch (t.getUrgencyCode()) {
            case "30":
                if (t.getCreateByAuto()!= null && t.getCreateByAuto() == 1) {
                    item.setBusniss_code(1);
                    item.setBusniess_name("新品");
                } else {
                    item.setBusniss_code(2);
                    item.setBusniess_name("次新品");
                }
                item.setFile_path(t.getArtImagePath());
                break;
            case "20":
                item.setBusniss_code(3);
                item.setBusniess_name("优化");
                item.setFile_path(t.getArtImagePath());
                break;
            case "10":
                item.setBusniss_code(4);
                item.setBusniess_name("A+");
                item.setFile_path(t.getArtImagePath());
                break;
            case "60":
                if ("1".equals(t.getThreeDPlatform())) {
                    item.setBusniss_code(5);
                    item.setBusniess_name("3D-公司建模");
                    if ("30".equals(t.getParent_UrgencyCode())) {
                        item.setBusniss_code(6);
                        item.setBusniess_name("3D新品-公司建模");
                    }
                    item.setFile_path(t.getLaterStagePath());
                } else if ("2".equals(t.getThreeDPlatform())) {
                    item.setBusniss_code(7);
                    item.setBusniess_name("3D-酷家乐");
                    item.setFile_path(t.getRenderPath());
                }
                break;
            case "90":
                item.setBusniss_code(8);
                item.setBusniess_name("Temu专属");
                item.setFile_path(t.getArtImagePath());
                break;
        }

        if (t.getCheckState()!= null && t.getCheckState() == 1) {
            item.setCompletedFlag(1);
        } else {
            item.setCompletedFlag(0);
        }

        String cpbh = t.getSku();
        String[] cpbhs = cpbh.split(",");
        List<String> list = new ArrayList<>();
        for (int i = 0; i < cpbhs.length; i++) {
            list.add(cpbhs[i]);
        }

        if (!item.getFile_path().isEmpty()) {
            // 使用正则表达式来分割字符串
            String[] paths = item.getFile_path().split("(?<!^\\\\)\\\\(?=\\\\)");

            for (String path : paths) {
                if (!path.isEmpty()) {
                    TemuOMArt _item = item.copy(); // 假设这里有一个copy方法来克隆对象
                    String sharePath = "\\" + path;
                    List<String> _paths = new ArrayList<>();
                    try {
                        // 消除路径中的空格，消除最后一个特殊字符，比如，或者；
                        sharePath = sharePath.trim().replaceAll("[,;，]$", "").trim();
                        getAllFilesInDirectory(sharePath, _paths,list);
                    } catch (Exception e) {
                        return null;
                    }
                    _item.setFile_path(sharePath);
                    _item.getPicture_url().addAll(_paths);
                    if(!_item.getPicture_url().isEmpty())
                        result.add(_item);
                }
            }
        }



        Set<TemuOMArt> set = new LinkedHashSet<>(result);
        result = new ArrayList<>(set);
        //   return result;
        List<CompletableFuture<TemuOMArt>> futures = new ArrayList<>();

        List<PIMPMMinioImagePathVo> pimpmMinioImagePathList = pimpmMinioImagePathMapper.queryMinioPath(cpbh);

        for (TemuOMArt temuOMArtResult : result) {
            List<String> imagePathBases = new ArrayList<>(temuOMArtResult.getPicture_url());
            temuOMArtResult.setPicture_url(new ArrayList<>());


            // 创建异步任务
            CompletableFuture<TemuOMArt> future = CompletableFuture.supplyAsync(() -> {
                List<PIMPMMinioImagePathVo> uploadedUrls = imagePathBases.stream()
                        .map(imagePathBase -> {
                            String localFilePath = null;
                            try {
                                String country = temuOMArtResult.getCountry();
                                String busniessName = temuOMArtResult.getBusniess_name();
                                String filePath = temuOMArtResult.getFile_path();
                                String nativeFileName = minIOHelper.processFile(imagePathBase);
                                String nativeUrl = serverUrl + nativeFileName;
                                localFilePath = destPath + nativeFileName;
                                PIMPMMinioImagePathVo pimpmMinioImagePath = null;
                                for (int i = 0; i < pimpmMinioImagePathList.size(); i++) {
                                    PIMPMMinioImagePathVo pimpmMinioImagePathVo = pimpmMinioImagePathList.get(i);
                                    if(pimpmMinioImagePathVo.getSharePath().equals(nativeUrl)){
                                        pimpmMinioImagePath = pimpmMinioImagePathVo;
                                        pimpmMinioImagePathVo.setCountry(country);
                                        pimpmMinioImagePathVo.setFileType(busniessName);
                                        pimpmMinioImagePathVo.setFilePath(filePath);
                                        pimpmMinioImagePathMapper.update(pimpmMinioImagePathVo);
                                    }
                                }
                                String[] skus = cpbh.split("[^A-Za-z0-9-]+");
                                for (int i = 0; i < skus.length; i++) {
                                    String sku = skus[i];
                                    PIMCpbhImageTypeDimensionVo pimCpbhImageTypeDimensionVo = new PIMCpbhImageTypeDimensionVo();
                                    pimCpbhImageTypeDimensionVo.setSku(sku);
                                    pimCpbhImageTypeDimensionVo.setCountry(country);
                                    pimCpbhImageTypeDimensionVo.setFileType(busniessName);
                                    pimCpbhImageTypeDimensionMapper.insert(pimCpbhImageTypeDimensionVo);
                                }
                                if(null!=pimpmMinioImagePath){
                                    return pimpmMinioImagePath;
                                } else {
                                    String minioPathNew = minIOHelper.uploadToMinIO(imagePathBase,localFilePath);
                                    PIMPMMinioImagePathVo pimpmMinioImagePathVo = new PIMPMMinioImagePathVo();
                                    pimpmMinioImagePathVo.setSku(cpbh);
                                    pimpmMinioImagePathVo.setSharePath(nativeUrl);
                                    pimpmMinioImagePathVo.setMinioPath(minioPathNew);
                                    pimpmMinioImagePathVo.setCountry(country);
                                    pimpmMinioImagePathVo.setFileType(busniessName);
                                    pimpmMinioImagePathVo.setFilePath(filePath);
                                    //新增一条的id
                                    pimpmMinioImagePathMapper.insert(pimpmMinioImagePathVo);
                                    Long id = pimpmMinioImagePathVo.getId();
                                    pimpmMinioImagePathVo.setId(pimpmMinioImagePathVo.getId());
                                    return pimpmMinioImagePathVo;
                                }
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
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                // 将上传的URL添加到原始对象的 PictureUrl 中
                temuOMArtResult.getPictureInfo().addAll(uploadedUrls);
                return temuOMArtResult;
            }, taskExecutor);

            futures.add(future);
        }

// 等待所有任务完成并收集结果
        List<TemuOMArt> updatedResults = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());


        return updatedResults;

    }

    public void getChildFilesInDirectory(String directory, List<String> filePaths) {
        // 路径规范化和验证
        File dir = new File(normalizePath(directory));
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory path: " + directory);
        }

        // 获取当前文件夹中的所有图片文件路径（不处理子文件夹）
        File[] imageFiles = listImageFiles(dir);

        if (imageFiles != null) {
            for (File file : imageFiles) {
                if (file.isFile()) {
                    filePaths.add(file.getAbsolutePath());
                }
            }
        }

        // 获取当前文件夹中的所有子文件夹
        try (DirectoryStream<Path> subdirectories = Files.newDirectoryStream(dir.toPath(), "*")) {
            for (Path subdirectory : subdirectories) {
                if (Files.isDirectory(subdirectory)) {
                    getChildFilesInDirectory(subdirectory.toString(), filePaths);
                }
            }
        } catch (IOException e) {
            // 记录异常信息
            System.err.println("Error accessing directory: " + directory);
            e.printStackTrace();
        }
    }

    private File[] listImageFiles(File dir) {
        return dir.listFiles((dir1, name) -> isSupportedImageFile(name));
    }

    private boolean isSupportedImageFile(String name) {
        for (String ext : SUPPORTED_IMAGE_EXTENSIONS) {
            if (name.toLowerCase().endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        File file = new File(path);
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid path: " + path, e);
        } finally {
            file.delete();
        }
    }

    public List<String> initCountry(List<String> areaList){
        List<PmCountryConfig> pmCountryConfigs = baseMapper.queryAllPMCountryConfig();
        List<String> countryList = new ArrayList<>();
        for (String area : areaList) {
            for (PmCountryConfig pmCountryConfig : pmCountryConfigs) {
                if (area.equalsIgnoreCase(pmCountryConfig.getCountry())) {
                    countryList.add(pmCountryConfig.getCountryArea());
                }
            }
        }

        return countryList;
    }
}
