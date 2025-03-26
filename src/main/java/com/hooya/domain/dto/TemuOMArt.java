package com.hooya.domain.dto;

import com.hooya.domain.vo.PIMPMMinioImagePathVo;
import com.hooya.domain.vo.PIMPMMinioQualityFilePathVo;
import com.hooya.domain.vo.PIMQualityFilePathVo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TemuOMArt {

    private String cpbh;
    private String country;
    private String busniess_name;
    private int busniss_code;
    private String file_path;
    private List<String> picture_url = new ArrayList<>();

    private List<PIMPMMinioImagePathVo> pictureInfo = new ArrayList<>();

    private List<PIMPMMinioQualityFilePathVo> qualityInfo = new ArrayList<>();

    private List<PIMQualityFilePathVo> otherQualityInfo = new ArrayList<>();

    /**
     * 0 - 视觉未完成, 1 - 视觉已完成
     */
    private int completedFlag;
//    private String picAuditStatus;


    public TemuOMArt copy() {
        TemuOMArt copy = new TemuOMArt();
        copy.setCpbh(this.cpbh);
        copy.setCountry(this.country);
        copy.setBusniess_name(this.busniess_name);
        copy.setBusniss_code(this.busniss_code);
        copy.setFile_path(this.file_path);
        copy.setCompletedFlag(this.completedFlag);
//        copy.setPicAuditStatus(this.picAuditStatus);

        // 深拷贝 pictureUrl 列表
        copy.setPicture_url(new ArrayList<>(this.picture_url));
        copy.setPictureInfo(new ArrayList<>(this.pictureInfo));

        return copy;
    }
}
