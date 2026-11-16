package norym.logisticsdeliverysystem.mapper;

import norym.logisticsdeliverysystem.entity.Postman;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostmanMapper
{
    Postman findById(@Param("id") Long id);
}
