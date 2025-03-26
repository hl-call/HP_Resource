package com.hooya.domain.vo;

import lombok.Data;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/6 13:53
 **/
@Data
public class PIMQualityFilePathVo {
    private Long id;
    private String sku;
    private String minioPath;
    private String createTime;
    private String updateTime;
    private Integer isDisable;
    private Integer isDel;
    private String sharePath;
    private String fileName;
    private String belongingPath;
    private String fileMd5;
    private String country;
    private String fileGroup;
    private String fileType;
}
