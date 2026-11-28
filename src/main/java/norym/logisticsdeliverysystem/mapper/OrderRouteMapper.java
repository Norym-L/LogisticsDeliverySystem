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

    /** 按单号定位某快递员的最后一公里任务；不是他的单返回 null。 */
    norym.logisticsdeliverysystem.dto.PostmanTask findTaskForPostman(
            @Param("orderNo") String orderNo,
            @Param("postmanId") Long postmanId);

    /** 始发站待收件任务：按快递员所属站查询，状态为 AWAITING_RECEIPT。 */
    java.util.List<norym.logisticsdeliverysystem.dto.PostmanTask> findOriginReceiptTasks(
            @Param("stationId") Long stationId);

    /** 按单号定位某始发站的待收件任务；不属于该站返回 null。 */
    norym.logisticsdeliverysystem.dto.PostmanTask findOriginReceiptTask(
            @Param("orderNo") String orderNo,
            @Param("stationId") Long stationId);

    /** 完成最后一公里：只有派送中路段能改为已签收/已拒收，并写入备注。 */
    int finishLastMile(@Param("orderId") Long orderId,
                       @Param("seq") Integer seq,
                       @Param("status") RouteStatus status,
                       @Param("remark") String remark);

    /**
     * 派送失败：路段回到已到站、清空取件时间与快递员，
     * 让站点可以重新派给同一人或其他人。
     */
    int resetLastMile(@Param("orderId") Long orderId,
                      @Param("seq") Integer seq,
                      @Param("remark") String remark);
}
