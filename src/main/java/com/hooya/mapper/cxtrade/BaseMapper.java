package com.hooya.mapper.cxtrade;

import com.hooya.domain.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BaseMapper {

    List<ResPictureVo> queryResourcePictureByCpbh(@Param("cpbhList") List<String> cpbhList,@Param("cpbh") String cpbh, @Param("country") List<String> country);

    List<ResPictureVo> queryPicturePathByCpbh(@Param("cpbh") String cpbh);

    List<PmCountryConfig> queryAllPMCountryConfig();

    List<ResDescriptionVo> queryResourceDescriptionByCpbh(@Param("cpbhList") List<String> cpbhList,@Param("cpbh") String cpbh, @Param("country") List<String> country, @Param("languages") List<String> languages);

    List<ResPictureVo> queryResourcePictureByCpbh2(@Param("cpbhList") List<String> cpbhList,@Param("cpbh") String cpbh, @Param("country") List<String> country);

    List<ResCountryNum> queryCountryNum(@Param("cpbhList") List<String> cpbhList,@Param("cpbh") String cpbh, @Param("country") List<String> country);

    List<ResSuitVo> queryResourceSuit(@Param("cpbhList") List<String> cpbhList,@Param("cpbh") String cpbh,  @Param("country") List<String> country);

    List<ResVideoVo> queryResourceVideoByCpbh(@Param("cpbhList") List<String> cpbhList,@Param("cpbh") String cpbh, @Param("country") List<String> country);

    Integer updateImageDisableById(@Param("id") Long id,@Param("isDisable") Integer isDisable);

    // 查质检文件
    ResQcReportVo queryQualityFileByCpbh(@Param("cpbh") String cpbh, @Param("country") List<String> country);
}

