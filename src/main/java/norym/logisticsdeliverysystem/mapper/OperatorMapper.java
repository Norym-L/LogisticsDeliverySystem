package norym.logisticsdeliverysystem.mapper;

import norym.logisticsdeliverysystem.entity.Operator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperatorMapper
{
    Operator findByUsername(@Param("username") String username);

    Operator findById(@Param("id") Long id);
}
