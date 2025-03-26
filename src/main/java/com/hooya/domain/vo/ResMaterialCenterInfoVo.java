package com.hooya.domain.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @AUTHOR majiang
 * @DATE 2025/1/2 9:33
 **/
@Data
public class ResMaterialCenterInfoVo {
    private String cpbh;
    private String country;
    private String typeLev;
    private String saler;
    private String path;
    private String title;
    private Integer copywritingCount;
    private List<Map>fileUrlList;

}
