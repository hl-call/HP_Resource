package com.hooya.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/6 13:53
 **/
@Data
public class PIMPMMinioQualityFilePathVo {
    private Long id;
    private String sku;
    private String minioPath;
    private String createTime;
    private String updateTime;
    private Integer isDisable;
    private Integer isDel;
    private String realName;
    private String folderName;
    private String zipUrl;
    private String zipMd5;
    private String country;
    private String fileGroup;
    private String fileType;

    @TableField(exist = false)
    String groupName;



}
