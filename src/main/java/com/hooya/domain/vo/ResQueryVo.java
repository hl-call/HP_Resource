package com.hooya.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class ResQueryVo {
    private String cpbh;
    private List<String> area;
    private List<String> languages;

    private String type;
}
