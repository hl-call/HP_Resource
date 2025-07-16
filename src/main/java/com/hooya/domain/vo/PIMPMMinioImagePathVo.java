package com.hooya.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/6 13:53
 **/
@Data
public class PIMPMMinioImagePathVo {
    private Long id;
    private String sku;
    private String sharePath;
    private String minioPath;
    private String createTime;
    private String updateTime;
    private Integer isDisable;
    private String country;
    private Integer fileGroup;
    private String fileType;
    private String filePath;

    @TableField(exist = false)
    private String fileName;
}
