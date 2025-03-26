package com.hooya.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class ResUpdatePictureVo {
    private List<Long> ids;
    private Integer isDisable;
}
