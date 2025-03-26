package com.hooya.mapper.pim;

import com.hooya.domain.vo.PIMQualityFilePathVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/23 10:06
 **/
@Mapper
public interface PIMQualityFilePathMapper {
    // pim_quality_file_path 开始
    Integer insertQualityFile(@Param("pimQualityFilePathVo") PIMQualityFilePathVo pimQualityFilePathVo);
    Integer updateQualityFile(@Param("pimQualityFilePathVo") PIMQualityFilePathVo pimQualityFilePathVo);
    PIMQualityFilePathVo qeuryQualityFile(@Param("sku") String sku, @Param("fileName") String fileName, @Param("belongingPath") String belongingPath);
    List<PIMQualityFilePathVo> getBelongingPathByCpbh(@Param("sku") String sku);
    List<PIMQualityFilePathVo> queryByBelongingPath(@Param("sku") String sku,@Param("belongingPath") String belongingPath);
    // pim_quality_file_path 结束
}
