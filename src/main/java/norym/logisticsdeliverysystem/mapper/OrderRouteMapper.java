package norym.logisticsdeliverysystem.mapper;

import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderRouteMapper
{
    List<OrderRoute> findByOrderId(@Param("orderId") Long orderId);

    int insert(OrderRoute route);

    int updateStatus(@Param("orderId") Long orderId,
                     @Param("seq") Integer seq,
                     @Param("status") RouteStatus status);
}
