package com.hooya.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.hooya.domain.dto.CpbhExcelDto;
import com.hooya.domain.dto.TemuOMArt;
import com.hooya.util.MinIOHelper;
import com.hooya.util.UpdateQualityByCpbh;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/19 11:59
 **/
@Component
@Slf4j
public class CustomExcelListener extends AnalysisEventListener<CpbhExcelDto> {

    private final UpdateQualityByCpbh updateQualityByCpbhService;

    private final Set<String> seenCpbhs = Collections.newSetFromMap(new ConcurrentHashMap<>()); // 线程安全的 Set
    private final Set<String> processedCpbhs = Collections.newSetFromMap(new ConcurrentHashMap<>()); // 记录已处理的 CPBH
    private final List<CpbhExcelDto> batchData = new CopyOnWriteArrayList<>(); // 线程安全的 List
    private static final int BATCH_COUNT = 2000; // 每批处理的数量

    @Autowired
    public CustomExcelListener(UpdateQualityByCpbh updateQualityByCpbhService) {
        this.updateQualityByCpbhService = updateQualityByCpbhService;
    }

    @Override
    public void invoke(CpbhExcelDto data, AnalysisContext context) {
        String cpbh = data.getCpbh();
        if (cpbh == null || cpbh.isEmpty()) {
            log.warn("无效的 CPBH 数据: {}", data);
            return;
        }
        if (seenCpbhs.add(cpbh)) { // 如果 CPBH 是第一次出现，则添加到集合中
            batchData.add(data);
            if (batchData.size() >= BATCH_COUNT) {
                // 处理一批数据
                handleBatchData(batchData);
                batchData.clear(); // 清空当前批次的数据
            }
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 处理剩余的数据
        if (!batchData.isEmpty()) {
            handleBatchData(batchData);
            batchData.clear();
        }

        // 打印已处理的 CPBH
        log.info("已处理的 CPBH: {}", processedCpbhs);

        // 计算未处理的 CPBH
        Set<String> unprocessedCpbhs = new HashSet<>(seenCpbhs);
        unprocessedCpbhs.removeAll(processedCpbhs);
        log.info("未处理的 CPBH: {}", unprocessedCpbhs);
    }

    private void handleBatchData(List<CpbhExcelDto> batchData) {
        for (CpbhExcelDto data : batchData) {
            try {
                String cpbh = data.getCpbh();
                List<TemuOMArt> temuOMArts = updateQualityByCpbhService.processCpbh(cpbh);
                if (temuOMArts != null && !temuOMArts.isEmpty()) {
                    log.info("成功处理: {}", cpbh);
                    processedCpbhs.add(cpbh); // 记录已处理的 CPBH
                } else {
                    log.warn("未找到对应的 TemuOMArt 数据: {}", cpbh);
                }
            } catch (Exception e) {
                log.error("处理 CPBH 数据时发生异常: {}", data, e);
            }
        }
    }
}
