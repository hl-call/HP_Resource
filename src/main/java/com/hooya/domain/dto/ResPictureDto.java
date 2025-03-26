package com.hooya.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ResPictureDto implements Serializable {
    private String busniess_name;
    private List<TemuOMArt> temuOMArtList;
}
