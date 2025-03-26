package com.hooya.controller;

import com.hooya.domain.dto.*;
import com.hooya.domain.vo.*;
import com.hooya.mapper.cxtrade.BaseMapper;
import com.hooya.mapper.pim.PIMPMMinioImagePathMapper;
import com.hooya.util.MinIOHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/23 17:16
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/dataProcessing")
@Slf4j
public class MinioPathDataController {
    private final BaseMapper baseMapper;
    private final MinIOHelper minIOHelper;
    private final ExecutorService taskExecutor;
    @Autowired
    PIMPMMinioImagePathMapper pimpmMinioImagePathMapper;

    @Value("${serverUrl}")
    private String serverUrl;
    @Value("${destPath}")
    private String destPath;

    @PostMapping("/doData")
    public void doData() {
        // 获取处理的cpbh
        List<PIMPMMinioImagePathVo> pimpmMinioImagePathVos = pimpmMinioImagePathMapper.queryGroupCpbh();

        // 循环cpbh做查询图片和组合操作
        for (PIMPMMinioImagePathVo pimpmMinioImagePathVo : pimpmMinioImagePathVos) {
            String cpbh = pimpmMinioImagePathVo.getSku();
            List<ResPictureVo> resPictureVos = baseMapper.queryPicturePathByCpbh(cpbh);

            // 提交任务
            for (ResPictureVo resPictureVo : resPictureVos) {
                artTaskToQMArt(resPictureVo);
            }

            // 处理组合
            List<ResPictureVo> artWorksList = baseMapper.queryResourcePictureByCpbh2(null,cpbh, null);
            pimpmMinioImagePathMapper.updateFileGroupByCpbh(cpbh, artWorksList.size());
        }
    }

    public List<TemuOMArt> artTaskToQMArt(ResPictureVo t) {
        List<TemuOMArt> result = new ArrayList<>();
        TemuOMArt item = new TemuOMArt();
        item.setCpbh(t.getSku());
        item.setCountry(t.getCountry());

        switch (t.getUrgencyCode()) {
            case "30":
                if (t.getCreateByAuto() != null && t.getCreateByAuto() == 1) {
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
        }

        if (t.getCheckState() != null && t.getCheckState() == 1) {
            item.setCompletedFlag(1);
        } else {
            item.setCompletedFlag(0);
        }

        String cpbh = t.getSku();
        String[] cpbhs = cpbh.split(",");
        List<String> list = Arrays.asList(cpbhs);

        if (item.getFile_path() != null && !item.getFile_path().isEmpty()) {
            String[] paths = item.getFile_path().split("(?<!^\\\\)\\\\(?=\\\\)");

            for (String path : paths) {
                if (path == null || path.isEmpty()) {
                    log.warn("Empty or null path encountered: {}", path);
                    continue;
                }

                TemuOMArt _item = item.copy(); // 假设这里有一个copy方法来克隆对象
                String sharePath = "\\" + path;
                List<String> _paths = new ArrayList<>();

                try {
                    sharePath = sharePath.trim().replaceAll("[,;，]$", "").trim();
                    if (sharePath.isEmpty()) {
                        log.warn("Processed sharePath is empty: {}", sharePath);
                        continue;
                    }
                    getAllFilesInDirectory(sharePath, _paths, list);
                } catch (Exception e) {
                    log.error("Failed to get files in directory: {}", sharePath, e);
                    continue;
                }
                _item.setFile_path(sharePath);
                _item.getPicture_url().addAll(_paths);
                if (!_item.getPicture_url().isEmpty())
                    result.add(_item);
            }
        }

        Set<TemuOMArt> set = new LinkedHashSet<>(result);
        result = new ArrayList<>(set);

        List<PIMPMMinioImagePathVo> pimpmMinioImagePathList = pimpmMinioImagePathMapper.queryMinioPath(cpbh);
        for (TemuOMArt temuOMArtResult : result) {
            List<String> imagePathBases = new ArrayList<>(temuOMArtResult.getPicture_url());
            temuOMArtResult.setPicture_url(new ArrayList<>());
            String country = temuOMArtResult.getCountry();
            String busniessName = temuOMArtResult.getBusniess_name();

            for (int i = 0; i < imagePathBases.size(); i++) {
                String imagePathBase = imagePathBases.get(i);
                String localFilePath = null;
                try {
                    String nativeFileName = minIOHelper.processFile(imagePathBase);
                    String nativeUrl = serverUrl + nativeFileName;
                    localFilePath = destPath + nativeFileName;
                    PIMPMMinioImagePathVo pimpmMinioImagePath = pimpmMinioImagePathList.stream()
                            .filter(vo -> vo.getSharePath().equals(nativeUrl))
                            .findFirst()
                            .orElse(null);
                    if (pimpmMinioImagePath != null) {
                        pimpmMinioImagePath.setCountry(country);
                        pimpmMinioImagePath.setFileType(busniessName);
                        pimpmMinioImagePathMapper.update(pimpmMinioImagePath);
                    }
                } catch (Exception e) {
                    log.error("Failed to upload image: {}", imagePathBase, e);
                    return null;
                } finally {
                    if (localFilePath != null) {
                        try {
                            Files.delete(Paths.get(localFilePath));
                        } catch (IOException e) {
                            log.error("Failed to delete local file: {}", localFilePath, e);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".mp4"};

    public void getAllFilesInDirectory(String directory, List<String> filePaths, List<String> folderNames) {
        if (directory == null || directory.isEmpty()) {
            log.warn("Directory path is null or empty: {}", directory);
            return;
        }

        File dir = new File(normalizePath(directory));
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory path: " + directory);
        }

        File[] imageFiles = listImageFiles(dir);
        if (imageFiles != null) {
            for (File file : imageFiles) {
                if (file.isFile()) {
                    filePaths.add(file.getAbsolutePath());
                }
            }
        }

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
                                getAllFilesInDirectory(subdirectory.toString(), filePaths, folderNames);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error accessing directory: {}", directory, e);
        }
    }

    private File[] listImageFiles(File dir) {
        return dir.listFiles((dir1, name) -> isSupportedImageFile(name));
    }

    private boolean isSupportedImageFile(String name) {
        if (name == null) {
            return false;
        }
        for (String ext : SUPPORTED_IMAGE_EXTENSIONS) {
            if (name.toLowerCase().endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            log.warn("Path is null or empty: {}", path);
            return null;
        }
        File file = new File(path);
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid path: " + path, e);
        }
    }
}
