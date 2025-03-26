package com.hooya.controller;

import com.hooya.domain.dto.Result;
import com.hooya.domain.dto.TemuOMArt;
import com.hooya.domain.vo.PIMPMMinioImagePathVo;
import com.hooya.domain.vo.ResPictureVo;
import com.hooya.domain.vo.ResQueryVo;
import com.hooya.mapper.cxtrade.BaseMapper;
import com.hooya.mapper.pim.PIMCpbhImageTypeDimensionMapper;
import com.hooya.mapper.pim.PIMPMMinioImagePathMapper;
import com.hooya.util.MinIOHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @AUTHOR majiang
 * @DATE 2025/1/16 9:26
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/resPicNew")
@Slf4j
public class MinioPictureController {
    public final BaseMapper baseMapper;

    public final MinIOHelper minIOHelper;

    public final ExecutorService taskExecutor;

    @Autowired
    PIMPMMinioImagePathMapper pimpmMinioImagePathMapper;

    @Autowired
    PIMCpbhImageTypeDimensionMapper pimCpbhImageTypeDimensionMapper;

    private List<String> processCpbh(String cpbh) {
        if (!StringUtils.hasText(cpbh)) {
            return null;
        }
        String[] split = cpbh.split(",");
        List<String> cpbhList = Arrays.asList(split);
        return cpbhList.size() > 1 ? cpbhList : null;
    }

    @PostMapping("/listPictures")
    @Transactional
    public Result<Object> listPictures(@RequestBody ResQueryVo resQueryVo) {

        if(!StringUtils.hasText(resQueryVo.getCpbh())){
            return new Result<>(500, null, "cpbh不能为空!");
        }
        List<TemuOMArt> allTemuOMArt =new ArrayList<>();
        List<String> cpbhList = processCpbh(resQueryVo.getCpbh());
        String cpbhQuery = "";
        if(cpbhList==null){
            cpbhQuery =  resQueryVo.getCpbh();
            List<PIMPMMinioImagePathVo> fileTypeByCpbh = pimpmMinioImagePathMapper.getFileTypeByCpbh(cpbhQuery);
            for (int i = 0; i < fileTypeByCpbh.size(); i++) {
                String busniessName = fileTypeByCpbh.get(i).getFileType();
                String filePath = fileTypeByCpbh.get(i).getFilePath();
                String country = fileTypeByCpbh.get(i).getCountry();
                String sku = fileTypeByCpbh.get(i).getSku();
                List<PIMPMMinioImagePathVo> pimpmMinioImagePathVos = pimpmMinioImagePathMapper.queryPictureMinioPath(cpbhQuery,busniessName);
                TemuOMArt temuOMArt = new TemuOMArt();
                temuOMArt.setBusniess_name(busniessName);
                temuOMArt.setFile_path(filePath);
                temuOMArt.setCountry(country);
                temuOMArt.setCpbh(sku);
                temuOMArt.setPictureInfo(pimpmMinioImagePathVos);
                allTemuOMArt.add(temuOMArt);
            }
        } else {
            for (int i = 0; i < cpbhList.size(); i++) {
                String cpbh = cpbhList.get(i);
                List<PIMPMMinioImagePathVo> fileTypeByCpbh = pimpmMinioImagePathMapper.getFileTypeByCpbh(cpbh);
                for (int j = 0; j < fileTypeByCpbh.size(); j++) {
                    String busniessName = fileTypeByCpbh.get(j).getFileType();
                    String filePath = fileTypeByCpbh.get(j).getFilePath();
                    String country = fileTypeByCpbh.get(i).getCountry();
                    String sku = fileTypeByCpbh.get(i).getSku();
                    List<PIMPMMinioImagePathVo> pimpmMinioImagePathVos = pimpmMinioImagePathMapper.queryPictureMinioPath(cpbh,busniessName);
                    TemuOMArt temuOMArt = new TemuOMArt();
                    temuOMArt.setBusniess_name(busniessName);
                    temuOMArt.setFile_path(filePath);
                    temuOMArt.setCountry(country);
                    temuOMArt.setCpbh(sku);
                    temuOMArt.setPictureInfo(pimpmMinioImagePathVos);
                    allTemuOMArt.add(temuOMArt);
                }
            }
        }


        return new Result<>(200, allTemuOMArt, "操作成功!");
    }



}
