package com.hooya.mapper.pim;

import com.hooya.domain.vo.PIMPMMinioQualityFilePathVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PIMPMMinioQualityFilePathMapper {

    // PIM_PMM_MINIO_QUALITY_FILE_PATH 开始
    Integer insertPpQualityFile(@Param("pimpmMinioQualityFilePathVo") PIMPMMinioQualityFilePathVo pimpmMinioQualityFilePathVo);

    Integer updatePpQualityFile(@Param("pimpmMinioQualityFilePathVo") PIMPMMinioQualityFilePathVo pimpmMinioQualityFilePathVo);

    PIMPMMinioQualityFilePathVo queryQualityMinioPath(@Param("cpbh")  String cpbh,@Param("realName")  String realName,@Param("folderName")  String folderName);

    PIMPMMinioQualityFilePathVo queryRarMd5(@Param("cpbh")  String cpbh,@Param("downloadUrl")  String downloadUrl);

    List<PIMPMMinioQualityFilePathVo> queryByCpbh(@Param("cpbh") String cpbh);
    List<PIMPMMinioQualityFilePathVo>  queryByBtwj(@Param("cpbh") String cpbh);
    List<PIMPMMinioQualityFilePathVo>  queryByZs(@Param("cpbh") String cpbh);
    // PIM_PMM_MINIO_QUALITY_FILE_PATH 结束

}
