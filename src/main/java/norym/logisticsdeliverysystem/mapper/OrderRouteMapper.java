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

    OrderRoute findByOrderIdAndSeq(@Param("orderId") Long orderId,
                                   @Param("seq") Integer seq);

    /** 指派该段的承运人与车次，只接受尚未到达的路段 */
    int assign(@Param("orderId") Long orderId,
               @Param("seq") Integer seq,
               @Param("postmanId") Long postmanId,
               @Param("tripNo") String tripNo);

    /** 记录从本站发出的时间 */
    int markDepart(@Param("orderId") Long orderId,
                   @Param("seq") Integer seq,
                   @Param("now") java.time.LocalDateTime now);

    /** 确认到站 */
    int markArrive(@Param("orderId") Long orderId,
                   @Param("seq") Integer seq,
                   @Param("now") java.time.LocalDateTime now);

    int deleteByOrderId(@Param("orderId") Long orderId);
}
