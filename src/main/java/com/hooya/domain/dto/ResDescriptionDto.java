package com.hooya.domain.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ResDescriptionDto {
    private Long id;

    private String country;

    private String cpbh;

    private String language;

    private String specialOption;

    private Map<String, String> data_info;

    public ResDescriptionDto(Long id, String country, String cpbh, String language,String specialOption, Map<String, String> data_info) {
        this.id = id;
        this.country = country;
        this.cpbh = cpbh;
        this.language = language;
        this.data_info = data_info;
        this.specialOption=specialOption;
    }
}
