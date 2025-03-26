package com.hooya.mapper.pim;

import com.hooya.domain.vo.ResCpSalerInfoVo;
import com.hooya.domain.vo.ResCpbhCountryVo;
import com.hooya.domain.vo.ResPrdTypeInfoVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MaterialCenterMapper {
    List<ResCpbhCountryVo> getCpbhCountry();
    Map<String, Object> getPrdTypeInfo(@Param("cpbh") String cpbh, @Param("country") String country);
    Map<String, Object> getCpSalerInfo(@Param("cpbh") String cpbh, @Param("country") String country);
    String getPathByCpbh(@Param("cpbh") String cpbh);
    Map<String, Object> getTitleAndBtsl(@Param("cpbh") String cpbh, @Param("country") String country);
    Integer getCpsmsSl(@Param("cpbh") String cpbh);
}
