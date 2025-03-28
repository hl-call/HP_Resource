package com.hooya.controller;


import com.hooya.domain.dto.*;
import com.hooya.domain.vo.*;
import com.hooya.mapper.cxtrade.BaseMapper;
import com.hooya.mapper.pim.PIMCpbhImageTypeDimensionMapper;
import com.hooya.mapper.pim.PIMPMMinioImagePathMapper;
import com.hooya.mapper.pim.PIMPMMinioQualityFilePathMapper;
import com.hooya.util.MinIOHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;



@RestController
@RequiredArgsConstructor
@RequestMapping("/resPic")
@Slf4j
public class ResourcePictureController {

    @Autowired
    public BaseMapper baseMapper;

    public final MinIOHelper minIOHelper;

    public final ExecutorService taskExecutor;

    @Autowired
    PIMPMMinioImagePathMapper pimpmMinioImagePathMapper;

    @Autowired
    PIMCpbhImageTypeDimensionMapper pimCpbhImageTypeDimensionMapper;

    @Autowired
    PIMPMMinioQualityFilePathMapper pimpmMinioQualityFilePathMapper;

    @Value("${serverUrl}")
    private String serverUrl;

    @Value("${destPath}")
    private String destPath;

    @PostMapping("/listPictures")
    @Transactional
    public Result<Object> listPictures(@RequestBody ResQueryVo resQueryVo) {
        if(!StringUtils.hasText(resQueryVo.getCpbh())){
            return new Result<>(500, null, "cpbh不能为空!");
        }
        List<String> cpbhList = processCpbh(resQueryVo.getCpbh());
        String cpbhQuery = "";
        if(cpbhList==null){
            cpbhQuery =  resQueryVo.getCpbh();
        }


        List<String> countryList = resQueryVo.getArea();

        List<ResPictureVo> resPictureVos = baseMapper.queryResourcePictureByCpbh(cpbhList,cpbhQuery,countryList);

        List<TemuOMArt> allTemuOMArt =new ArrayList<>();



        // 存储 Future 对象
        List<CompletableFuture<List<TemuOMArt>>> futures = new ArrayList<>();
        // 提交任务
        for (ResPictureVo resPictureVo : resPictureVos) {
            CompletableFuture<List<TemuOMArt>> future = CompletableFuture.supplyAsync(() -> {
                List<TemuOMArt> temuOMArts = artTaskToQMArt(resPictureVo);
                // 异步任务逻辑
                return temuOMArts != null ? temuOMArts : Collections.emptyList();
            }, taskExecutor);
            futures.add(future);
        }

        // 获取并合并所有结果
        try {
            for (Future<List<TemuOMArt>> future : futures) {
                List<TemuOMArt> result = future.get(); // 获取结果
                allTemuOMArt.addAll(result);
            }
        } catch (InterruptedException | ExecutionException e) {
            // 处理异常
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error while processing tasks", e);
        }


        return new Result<>(200, allTemuOMArt, "操作成功!");
    }




    @PostMapping("/listDescriptions")
    public Result<Object> listDescriptions(@RequestBody ResQueryVo resQueryVo){
        if(!StringUtils.hasText(resQueryVo.getCpbh())){
            return new Result<>(500, null, "cpbh不能为空!");
        }
        List<String> cpbhList = processCpbh(resQueryVo.getCpbh());
        String cpbhQuery = "";
        if(cpbhList==null){
            cpbhQuery =  resQueryVo.getCpbh();
        }

        //   List<String> countryList = initCountry(resQueryVo.getArea());
        List<String> countryList = resQueryVo.getArea();
        List<String> languageList = resQueryVo.getLanguages();
        List<ResDescriptionVo> resDescriptionVos = baseMapper.queryResourceDescriptionByCpbh(cpbhList,cpbhQuery, countryList,languageList);

        List<ResDescriptionDto> resDescriptionDtos = new ArrayList<>();

        for (ResDescriptionVo resDescriptionVo : resDescriptionVos) {
            Map<String, String> map = new HashMap<>();
            map.put("features", resDescriptionVo.getFeature());
            map.put("bulletPoint", resDescriptionVo.getBulletPoint());
            map.put("description", resDescriptionVo.getDescription());
            map.put("title", resDescriptionVo.getTitle());
            map.put("specifications", resDescriptionVo.getSpecification());
            resDescriptionDtos.add(new ResDescriptionDto(
                    resDescriptionVo.getId(),resDescriptionVo.getCountry(),resDescriptionVo.getCpbh(), resDescriptionVo.getLanguage(),resDescriptionVo.getSpecialOption(), map));
        }

        return new Result<>(200,resDescriptionDtos,"操作成功");
    }

    @PostMapping("/listSuite")
    @Transactional
    public Result<Object> listSuite(@RequestBody ResQueryVo resQueryVo){
        if(!StringUtils.hasText(resQueryVo.getCpbh())){
            return new Result<>(500, null, "cpbh不能为空!");
        }
        List<String> cpbhList = processCpbh(resQueryVo.getCpbh());
        String cpbhQuery = "";
        if(cpbhList==null){
            cpbhQuery =  resQueryVo.getCpbh();
        }


        List<String> countryList = resQueryVo.getArea();


        List<ResPictureVo> artWorksList = baseMapper.queryResourcePictureByCpbh2(cpbhList,cpbhQuery,countryList);
        List<ResCountryNum> queryCountryNum = baseMapper.queryCountryNum(cpbhList,cpbhQuery,countryList);
        List<ResSuitVo> waBaseResult = baseMapper.queryResourceSuit(cpbhList,cpbhQuery, countryList);

        // 处理 waBaseResult
        List<TemuOMSuite> waResultList = new ArrayList<>();
        for (ResSuitVo waResult : waBaseResult) {
            TemuOMSuite tmpModel = new TemuOMSuite();
            tmpModel.setLanguage(waResult.getLanguaeg());
            tmpModel.setWAID(String.valueOf(waResult.getWaid()));
            tmpModel.setId(String.valueOf(waResult.getId()));
            tmpModel.setCpbh(waResult.getSku());
            tmpModel.setCountry(waResult.getCountry());

            TemuDataInfoOM dataInfo = new TemuDataInfoOM();
            dataInfo.setTitle(waResult.getTitle());
            dataInfo.setBulletPoint(waResult.getBulletPoint());
            dataInfo.setDescription(waResult.getDescription());
            dataInfo.setFeatures(waResult.getFeature());
            dataInfo.setSpecifications(waResult.getSpecification());
            tmpModel.setData_info(dataInfo);

            waResultList.add(tmpModel);
        }

        // 生成最终的 suiteList
        List<TemuSuiteOM> suiteList = new ArrayList<>();
        for (ResPictureVo item : artWorksList) {
            TemuSuiteOM tmpSuite = new TemuSuiteOM();
            tmpSuite.setPictures(new ArrayList<>());
            tmpSuite.setDescriptions(new ArrayList<>());
            tmpSuite.setUser_info(new TemuUser());

            // 获取与当前 SKU 相同的 artList
            List<ResPictureVo> artList = artWorksList.stream()
                    .filter(t -> t.getSku().equals(item.getSku()))
                    .collect(Collectors.toList());

            // 调用 ArtTaskToQMArt 方法，假设此方法已经实现
            for (ResPictureVo t : artList) {
                tmpSuite.getPictures().addAll(artTaskToQMArt(t));  // 假设 ArtTaskToQMArt 已经实现
            }

            // 获取 WAID 列表并匹配描述信息
            List<String> tmpWAIDList = artWorksList.stream()
                    .map(t -> String.valueOf(t.getWaid()))
                    .distinct()
                    .collect(Collectors.toList());

            if (tmpWAIDList != null && !tmpWAIDList.isEmpty()) {
                List<TemuOMSuite> waList = waResultList.stream()
                        .filter(t -> tmpWAIDList.contains(t.getWAID()))
                        .collect(Collectors.toList());
                tmpSuite.getDescriptions().addAll(waList);
            }

            // 设置用户信息
            tmpSuite.getUser_info().setUser_id(String.valueOf(item.getAmzyyUserID()));
            tmpSuite.getUser_info().setUser_name(item.getAmzyyUserName());

            // 将生成的 tmpSuite 添加到 suiteList
            suiteList.add(tmpSuite);
        }
        String[] skus = resQueryVo.getCpbh().split("[^A-Za-z0-9-]+");
        for (int i = 0; i < queryCountryNum.size(); i++) {
            ResCountryNum resCountryNum = queryCountryNum.get(i);
            String country = resCountryNum.getCountry();
            Integer num = resCountryNum.getNum();
            for (int j = 0; j < skus.length; j++) {
                String sku = skus[j];
                Integer fileGroupNum = pimCpbhImageTypeDimensionMapper.queryGroupTypeByCpbh(sku, country);
                if(null==fileGroupNum||!fileGroupNum.equals(num)){
                    pimCpbhImageTypeDimensionMapper.updateFileGroupByCpbh(sku, num,country);
                }
            }
        }

        pimpmMinioImagePathMapper.updateFileGroupByCpbh(resQueryVo.getCpbh(),suiteList.size());

        return new Result<>(200, suiteList, "操作成功!");
    }

    @PostMapping("/putImgByNewQuality")
    public Boolean putImgByNewQuality() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("006", "产品");
        map.put("002", "大货");
        map.put("007", "测试");
        map.put("005", "摔箱");
        map.put("003", "外包装");

        for(String key : map.keySet()){
            List<QCMCheckPointCategoryVo> qcmCheckPointCategoryVoList = baseMapper.getQCMCheckPointCategory(key);
            for (QCMCheckPointCategoryVo qcmCheckPointCategoryVo : qcmCheckPointCategoryVoList) {
                qcmCheckPointCategoryVo.setType(map.get(key));
                PIMPMMinioQualityFilePathVo pimpmMinioImagePath = pimpmMinioQualityFilePathMapper.queryQualityMinioPathNew(qcmCheckPointCategoryVo.getCpbh(), qcmCheckPointCategoryVo.getMediaPath(), qcmCheckPointCategoryVo.getCountry(), qcmCheckPointCategoryVo.getOrderCode());
                if (null == pimpmMinioImagePath) {
                    pimpmMinioQualityFilePathMapper.insertNewQualityFile(qcmCheckPointCategoryVo);
                }
            }
        }

        return true;
    }

    @PostMapping("/listVideos")
    @Transactional
    public Result<Object> listVideos(@RequestBody ResQueryVo resQueryVo) {
        if(!StringUtils.hasText(resQueryVo.getCpbh())){
            return new Result<>(500, null, "cpbh不能为空!");
        }
        List<String> cpbhList = processCpbh(resQueryVo.getCpbh());
        String cpbhQuery = "";
        if(cpbhList==null){
            cpbhQuery =  resQueryVo.getCpbh();
        }

        List<String> countryList = resQueryVo.getArea();

        List<ResVideoVo> resVideoVos = baseMapper.queryResourceVideoByCpbh(cpbhList,cpbhQuery, countryList);

        List<TemuOMArt> allTemuOMArt =new ArrayList<>();


        // 存储 Future 对象
        List<CompletableFuture<List<TemuOMArt>>> futures = new ArrayList<>();
        // 提交任务
        for (ResVideoVo resVideoVo : resVideoVos) {
            CompletableFuture<List<TemuOMArt>> future = CompletableFuture.supplyAsync(() -> {
                List<TemuOMArt> temuOMArts = artTaskToQMArtVideo(resVideoVo);
                // 异步任务逻辑
                return temuOMArts != null ? temuOMArts : Collections.emptyList();
            }, taskExecutor);
            futures.add(future);
        }


// 获取并合并所有结果
        try {
            for (Future<List<TemuOMArt>> future : futures) {
                List<TemuOMArt> result = future.get(); // 获取结果
                allTemuOMArt.addAll(result);
            }
        } catch (InterruptedException | ExecutionException e) {
            // 处理异常
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error while processing tasks", e);
        }

        // 按 busniess_name 分组
//        Map<String, List<TemuOMArt>> busniess_name_Map = allTemuOMArt.stream()
//                .collect(Collectors.groupingBy(TemuOMArt::getBusniessName));

        return new Result<>(200, allTemuOMArt, "操作成功!");
    }


    @PostMapping("/updatePicture")
    @Transactional
    public Result<Object> updatePicture(@RequestBody ResUpdatePictureVo resUpdatePictureVo) {
        if(null==resUpdatePictureVo.getIds()){
            return new Result<>(500, null, "所选图片id不能为空!");
        }
        List<Long> ids = resUpdatePictureVo.getIds();
        Integer isDisable = resUpdatePictureVo.getIsDisable();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            pimpmMinioImagePathMapper.updateImageDisableById(id, isDisable);
        }
        return new Result<>(200, "", "操作成功!");
    }


    @Transactional
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

        // 使用本地路径存储

/*        for (TemuOMArt temuOMArtResult : result) {
            List<String> imagePathBases = new ArrayList<>(temuOMArtResult.getPictureUrl());
            temuOMArtResult.setPictureUrl(new ArrayList<>());

            for (String basePath : imagePathBases) {
                String nativeFileName = minIOHelper.processFile(basePath);

                String nativeUrl = serverUrl + nativeFileName;

                temuOMArtResult.getPictureUrl().add(nativeUrl);
            }
        }*/




     //   return result;

        // 转换成miniIO路径
/*
        for (TemuOMArt temuOMArtResult : result) {
            List<String> imagePathBases = new ArrayList<>(temuOMArtResult.getPictureUrl());
            temuOMArtResult.setPictureUrl(new ArrayList<>());

            for (String basePath : imagePathBases) {
                String minIOReturn = minIOHelper.uploadToMinIO(basePath);
                temuOMArtResult.getPictureUrl().add(minIOReturn);
            }
        }
*/


        Set<TemuOMArt> set = new LinkedHashSet<>(result);
        result = new ArrayList<>(set);
     //   return result;
        List<CompletableFuture<TemuOMArt>> futures = new ArrayList<>();
        // 2024.12.09前版本
 /*       for (TemuOMArt temuOMArtResult : result) {
            List<String> imagePathBases = new ArrayList<>(temuOMArtResult.getPicture_url());
            temuOMArtResult.setPicture_url(new ArrayList<>());

            // 创建异步任务
            CompletableFuture<TemuOMArt> future = CompletableFuture.supplyAsync(() -> {
                List<String> uploadedUrls = imagePathBases.stream()
                        .map(imagePathBase -> {
                            String localFilePath = null;
                            try {
                                String nativeFileName = minIOHelper.processFile(imagePathBase);
                                String nativeUrl = serverUrl + nativeFileName;
                                localFilePath = destPath + nativeFileName;
                                PIMPMMinioImagePathVo pimpmMinioImagePath = pimpmMinioImagePathMapper.queryMinioPath(cpbh, nativeUrl);
                                if(null!=pimpmMinioImagePath){
                                    return pimpmMinioImagePath.getMinioPath();
                                } else {
                                    String minioPathNew = minIOHelper.uploadToMinIO(imagePathBase,localFilePath);
                                    PIMPMMinioImagePathVo pimpmMinioImagePathVo = new PIMPMMinioImagePathVo();
                                    pimpmMinioImagePathVo.setSku(cpbh);
                                    pimpmMinioImagePathVo.setSharePath(nativeUrl);
                                    pimpmMinioImagePathVo.setMinioPath(minioPathNew);
                                    //新增一条的id
                                    pimpmMinioImagePathMapper.insert(pimpmMinioImagePathVo);
                                    return minioPathNew;
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
                temuOMArtResult.getPicture_url().addAll(uploadedUrls);
                return temuOMArtResult;
            }, taskExecutor);

            futures.add(future);
        }*/

        List<PIMPMMinioImagePathVo> pimpmMinioImagePathList = pimpmMinioImagePathMapper.queryMinioPath(cpbh);
        // 12.12上线版
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


/*
        List<TemuOMArt> updatedResults = new ArrayList<>();
        for (TemuOMArt temuOMArtResult : result) {
            List<String> imagePathBases = new ArrayList<>(temuOMArtResult.getPicture_url());
            temuOMArtResult.setPicture_url(new ArrayList<>());
            List<String> uploadedUrls = minIOHelper.uploadBatchToMinIO(imagePathBases);
            temuOMArtResult.getPicture_url().addAll(uploadedUrls);
            updatedResults.add(temuOMArtResult);
        }
*/



        return updatedResults;

    }

    public List<TemuOMArt> artTaskToQMArtVideo(ResVideoVo t) {
        List<TemuOMArt> result = new ArrayList<>();
        TemuOMArt item = new TemuOMArt();
        item.setCpbh(t.getSku());
        item.setFile_path(t.getCameraImagePath());


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
                    _item.setFile_path(sharePath);
                    _paths.add(sharePath);
                    _item.setPicture_url(_paths);
                    if(!_item.getPicture_url().isEmpty())
                        result.add(_item);
                }
            }
        }
        return result;
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

    private List<String> processCpbh(String cpbh) {
        if (!StringUtils.hasText(cpbh)) {
            return null;
        }
        String[] split = cpbh.split(",");
        List<String> cpbhList = Arrays.asList(split);
        return cpbhList.size() > 1 ? cpbhList : null;
    }
}
