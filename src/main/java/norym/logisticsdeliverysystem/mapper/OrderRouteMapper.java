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

    /** 指派该段的车次（中转段），只接受尚未到站的路段 */
    int assignTrip(@Param("orderId") Long orderId,
                   @Param("seq") Integer seq,
                   @Param("tripNo") String tripNo);

    /** 指派最后一公里的快递员，只落在目的站那一行 */
    int assignPostman(@Param("orderId") Long orderId,
                      @Param("seq") Integer seq,
                      @Param("postmanId") Long postmanId);

    /** 记录从本站发出的时间 */
    int markDepart(@Param("orderId") Long orderId,
                   @Param("seq") Integer seq,
                   @Param("now") java.time.LocalDateTime now);

    /** 确认到站 */
    int markArrive(@Param("orderId") Long orderId,
                   @Param("seq") Integer seq,
                   @Param("now") java.time.LocalDateTime now);

    int deleteByOrderId(@Param("orderId") Long orderId);

    /**
     * 快递员的任务列表。
     * <p>
     * 快递员只负责最后一公里，所以按**订单状态**筛，而不是路段状态：
     * 货到目的站后路段已是 ARRIVED，但仍在等快递员取件。
     *
     * @param orderStates 待办传 SENDING/DELIVERING；已完成传 RECEIVED/REFUSED
     */
    java.util.List<norym.logisticsdeliverysystem.dto.PostmanTask> findTasks(
            @Param("postmanId") Long postmanId,
            @Param("orderStates") java.util.List<String> orderStates);
}
