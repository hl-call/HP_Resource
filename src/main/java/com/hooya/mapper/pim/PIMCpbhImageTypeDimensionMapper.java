package com.hooya.mapper.pim;

import com.hooya.domain.vo.PIMCpbhImageTypeDimensionVo;
import com.hooya.domain.vo.PIMPMMinioImagePathVo;
import com.hooya.domain.vo.PIMPMMinioQualityFilePathVo;
import com.hooya.domain.vo.PIMQualityFilePathVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PIMCpbhImageTypeDimensionMapper {
    // pim_cpbh_image_type_dimension 开始
    Integer insert(@Param("pimCpbhImageTypeDimensionVo") PIMCpbhImageTypeDimensionVo pimCpbhImageTypeDimensionVo);

    Integer updateFileGroupByCpbh(@Param("sku") String sku, @Param("fileGroup") Integer fileGroup, @Param("country") String country);
    // pim_cpbh_image_type_dimension 结束

    Integer queryGroupTypeByCpbh(@Param("sku") String sku, @Param("country") String country);
}
