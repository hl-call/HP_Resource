package com.hooya.domain.dto;

import lombok.Data;

@Data
public class TemuOMSuite {
    private String WAID;
    private String id;
    private String cpbh;
    private String country;
    private String language;
    private TemuDataInfoOM data_info;
}
