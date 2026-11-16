package norym.logisticsdeliverysystem.mapper;

import norym.logisticsdeliverysystem.entity.RouteEdge;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RouteEdgeMapper
{
    /** 线路总量很小，一次性全量读进内存跑最短路，避免逐点查库 */
    List<RouteEdge> findAll();
}
