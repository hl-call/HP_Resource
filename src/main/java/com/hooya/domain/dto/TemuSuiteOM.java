package com.hooya.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class TemuSuiteOM {
    private List<TemuOMArt> pictures;
    private List<TemuOMSuite> descriptions;
    private TemuUser user_info;
}
