package com.hooya.domain.vo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
public class ResPictureVo {
    private Long id;
    private String sku;
    private String country;
    private String urgencyCode;
    private Integer createByAuto;
    private String artImagePath;
    private String threeDPlatform;
    private String parent_UrgencyCode;
    private String laterStagePath;
    private String renderPath;
    private Integer CheckState;
    private String amzyyUserID;
    private String amzyyUserName;
    private String waid;
}
