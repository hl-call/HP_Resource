package com.hooya.mapper.pim;

import com.hooya.domain.vo.PIMPMMinioImagePathVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PIMPMMinioImagePathMapper {
    // PIM_PMM_MINIO_IMAGE_PATH 开始
    List<PIMPMMinioImagePathVo> queryGroupCpbh();
    Integer insert(@Param("pimpmMinioImagePathVo") PIMPMMinioImagePathVo pimpmMinioImagePathVo);
    Integer update(@Param("pimpmMinioImagePathVo") PIMPMMinioImagePathVo pimpmMinioImagePathVo);
    Integer updateFileGroupByCpbh(@Param("sku") String sku, @Param("fileGroup") Integer fileGroup);
    List<PIMPMMinioImagePathVo> queryMinioPath(@Param("cpbh")  String cpbh);
    Integer updateImageDisableById(@Param("id") Long id,@Param("isDisable") Integer isDisable);
    List<PIMPMMinioImagePathVo> getFileTypeByCpbh(@Param("cpbh")  String cpbh);
    List<PIMPMMinioImagePathVo> queryPictureMinioPath(@Param("cpbh")  String cpbh,@Param("fileType")  String fileType);
    List<String> getAllSku();
    // PIM_PMM_MINIO_IMAGE_PATH 结束

}
