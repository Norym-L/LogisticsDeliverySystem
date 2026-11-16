package norym.logisticsdeliverysystem.mapper;

import norym.logisticsdeliverysystem.dto.PostmanInfo;
import norym.logisticsdeliverysystem.entity.Postman;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostmanMapper
{
    Postman findById(@Param("id") Long id);

    Postman findByUsername(@Param("username") String username);

    /** 管理端列表：带手里单量统计，stationId 为空时查全部 */
    List<PostmanInfo> findAllWithTaskCount(@Param("stationId") Long stationId);
}
