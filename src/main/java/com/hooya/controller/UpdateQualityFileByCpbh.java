package com.hooya.controller;

import com.alibaba.excel.EasyExcel;
import com.hooya.domain.dto.CpbhExcelDto;
import com.hooya.listener.CustomExcelListener;
import com.hooya.util.UpdateQualityByCpbh;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/19 9:51
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/updateQualityFile")
@Slf4j
public class UpdateQualityFileByCpbh {
    @Autowired
    private UpdateQualityByCpbh updateQualityByCpbhService;

    @PostMapping("/updateByCpbh")
    public void updateQualityFileByCpbhFromFile(String filePath) {
        CustomExcelListener listener = new CustomExcelListener(updateQualityByCpbhService);
        EasyExcel.read(filePath, CpbhExcelDto.class, listener)
                .sheet() // 默认读取第一个 sheet
                .headRowNumber(1) // 如果有表头，设置表头行数，默认为 1
                .registerReadListener(listener)
                .doRead();
    }


}
