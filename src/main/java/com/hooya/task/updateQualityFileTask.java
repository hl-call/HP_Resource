package com.hooya.task;

import com.alibaba.excel.EasyExcel;
import com.hooya.domain.dto.CpbhExcelDto;
import com.hooya.domain.dto.TemuOMArt;
import com.hooya.domain.vo.*;
import com.hooya.listener.CustomExcelListener;
import com.hooya.mapper.cxtrade.BaseMapper;
import com.hooya.mapper.pim.PIMPMMinioImagePathMapper;
import com.hooya.mapper.pim.PIMPMMinioQualityFilePathMapper;
import com.hooya.util.FilePathGet;
import com.hooya.util.MinioPicturePathGet;
import com.hooya.util.UpdateQualityByCpbh;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/19 9:46
 **/
@Component
@RequiredArgsConstructor
public class updateQualityFileTask {
    @Autowired
    UpdateQualityByCpbh updateQualityByCpbhService;

    @Autowired
    FilePathGet filePathGet;

    @Autowired
    PIMPMMinioImagePathMapper pimpmMinioImagePathMapper;

    @Autowired
    MinioPicturePathGet minioPicturePathGet;

    @Autowired
    public BaseMapper baseMapper;

    @Autowired
    PIMPMMinioQualityFilePathMapper pimpmMinioQualityFilePathMapper;



    public final ExecutorService taskExecutor;

    /**
     * 每日任务  查询压缩包是否存在不同
     *
     */
    @Scheduled(cron = "0 0 23 * * ?") // 每天23:00执行
    public void performNightlyUpdate() {
        String filePath = "C:\\Program Files\\cpbh\\cpbh.xlsx";
      // String filePath = "D:\\解密\\cpbh.xlsx";
        // 在这里编写你的定时任务逻辑
        System.out.println("执行夜间更新任务...");
        CustomExcelListener listener = new CustomExcelListener(updateQualityByCpbhService);
        EasyExcel.read(filePath, CpbhExcelDto.class, listener)
                .sheet() // 默认读取第一个 sheet
                .headRowNumber(1) // 如果有表头，设置表头行数，默认为 1
                .registerReadListener(listener)
                .doRead();
    }

    /**
     * 每日任务  查询共享磁盘
     *
     */
    @Scheduled(cron = "0 0 22 * * ?")
    public void performNightlyInsertAndUp() {
        List<String> filePathWps = new ArrayList<>();
        filePathWps.add("\\\\192.168.0.228\\5.7文案编辑项目部\\CPC 待做");
        filePathWps.add("\\\\192.168.0.228\\5.9综合平台\\公共区\\2024年\\吴丽丽做的资质");
        filePathWps.add("\\\\192.168.0.228\\5.9综合平台\\公共区\\2024年\\CPC-待做");
        List<String> filePathImages = new ArrayList<>();
        filePathImages.add("\\\\192.168.0.228\\5.9综合平台\\公共区\\2024年\\Temu标签打印");
        for (int i = 0; i < filePathWps.size(); i++) {
            String folderPath = filePathWps.get(i);
            // 创建文件对象
            File folder = new File(folderPath);

            // 检查文件夹是否存在且是一个目录
            if (folder.exists() && folder.isDirectory()) {
                // 遍历文件夹下的所有文件和子文件夹
                filePathGet.scanFiles(folder,folderPath);
            } else {
                System.err.println("指定的路径不是一个有效的文件夹: " + folderPath);
            }
        }
        for (int i = 0; i < filePathImages.size(); i++) {
            String folderPath = filePathImages.get(i);
            // 创建文件对象
            File folder = new File(folderPath);

            // 检查文件夹是否存在且是一个目录
            if (folder.exists() && folder.isDirectory()) {
                // 遍历文件夹下的所有文件和子文件夹
                filePathGet.scanImageFiles(folder,folderPath);
            } else {
                System.err.println("指定的路径不是一个有效的文件夹: " + folderPath);
            }
        }
        //String filePath = "D:\\解密\\cpbh-test.xlsx";
        // 在这里编写你的定时任务逻辑
        System.out.println("执行夜间更新任务...");
    }


    /**
     * 每日任务  根据pm2_sku_content_records_part表中的sku刷新pim_pm_minio_image_path表数据
     *
     */
    @Scheduled(cron = "0 15 22 * * ?") // 每天22:15执行
    public void performNightlyUpdateMinioPicture() {
        List<String> allSku = pimpmMinioImagePathMapper.getAllSku();

        if (allSku.isEmpty()) {
            return; // 简化边界条件处理
        }
        // 存储 Future 对象
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        // 提交任务
        for (String sku : allSku) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // 异步任务逻辑
                minioPicturePathGet.listPictures(sku);
                // 在这里处理每个sku，不需要返回数据
            }, taskExecutor);
            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        // 当所有任务都完成时，可以在这里进行一些后续操作
        allFutures.join();
        System.out.println("所有任务完成");
    }

    /**
     * 把质检系统的图片每天搬过来
     *
     */
    @Scheduled(cron = "0 00 02 * * ?")
    public  void putImgByNewQuality() {
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
    }
}
