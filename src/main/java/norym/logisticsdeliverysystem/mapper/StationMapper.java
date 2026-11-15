package norym.logisticsdeliverysystem.mapper;

import norym.logisticsdeliverysystem.entity.Station;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StationMapper
{
    List<Station> findAll();

    Station findById(@Param("id") Long id);
}
