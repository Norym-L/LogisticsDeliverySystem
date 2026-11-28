package norym.logisticsdeliverysystem.mapper;

import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeliveryOrderMapper
{
    int insert(DeliveryOrder order);

    DeliveryOrder findByOrderNo(@Param("orderNo") String orderNo);

    /** 原单对应的退回单，没有则返回 null */
    DeliveryOrder findByReturnOf(@Param("orderNo") String orderNo);

    /** 手机号同时匹配寄件人与收件人，否则收件人查不到发给自己的件 */
    List<DeliveryOrder> findByPhone(@Param("phone") String phone);

    /**
     * 签收：手机号、订单状态三个条件一起卡在 WHERE 里。
     * 返回 0 表示校验未通过或已被并发修改。
     */
    int sign(@Param("orderNo") String orderNo,
             @Param("receiverPhone") String receiverPhone,
             @Param("now") LocalDateTime now);

    /** 快递员妥投：条件仍是 DELIVERING，防止与用户端签收并发重复完成。 */
    int deliverSuccess(@Param("orderNo") String orderNo,
                       @Param("now") LocalDateTime now);

    /** 派送失败：DELIVERING 回 SENDING，路段由 service 重置为待再派。 */
    int deliveryFailed(@Param("orderNo") String orderNo);

    int refuse(@Param("orderNo") String orderNo,
               @Param("receiverPhone") String receiverPhone,
               @Param("reason") String reason,
               @Param("now") LocalDateTime now);

    /**
     * 用户取消：只允许首次发运前（WAITING / AWAITING_RECEIPT / APPROVED），
     * 同时用寄件人手机号做身份校验。
     */
    int cancel(@Param("orderNo") String orderNo,
               @Param("senderPhone") String senderPhone,
               @Param("now") LocalDateTime now);

    /**
     * 被拒后修改重提：只允许 REJECTED 且未超过重提上限。
     */
    int resubmit(@Param("order") DeliveryOrder order,
                 @Param("deadline") LocalDateTime deadline,
                 @Param("maxResubmit") int maxResubmit);

    /** 审批超时批量转 REJECTED，返回受影响行数 */
    int rejectTimeout(@Param("now") LocalDateTime now);

    /** 按状态查列表，管理端"待审批请求"用 */
    List<DeliveryOrder> findByState(@Param("state") OrderState state);

    /** 本站待发运：当前站是本站，下一段尚未到达，本站还没写出发车时间 */
    List<norym.logisticsdeliverysystem.dto.StationInboxItem> findPendingDepart(@Param("stationId") Long stationId);

    /** 本站待收件：批准后、始发站确认收件前 */
    List<norym.logisticsdeliverysystem.dto.StationInboxItem> findPendingReceipt(@Param("stationId") Long stationId);

    /** 本站待到货：下一段的站是本站，且上一段已经发运 */
    List<norym.logisticsdeliverysystem.dto.StationInboxItem> findPendingArrive(@Param("stationId") Long stationId);

    /** 本站待派单：货已到目的站，仍是运送中，还没有快递员 */
    List<norym.logisticsdeliverysystem.dto.StationInboxItem> findPendingDispatch(@Param("stationId") Long stationId);

    /** 批准：只认 WAITING，同时写入路线站数与起点位置 */
    int approve(@Param("orderNo") String orderNo,
                @Param("operatorId") Long operatorId,
                @Param("now") LocalDateTime now,
                @Param("stationCount") int stationCount,
                @Param("fromStationId") Long fromStationId);

    /**
     * 始发站确认收件并核价：只认 AWAITING_RECEIPT，
     * 同一事务里写入实测体积/质量、最终运费和收件时间，随后转 APPROVED。
     */
    int confirmReceipt(@Param("orderNo") String orderNo,
                       @Param("now") LocalDateTime now,
                       @Param("volumeCm3") java.math.BigDecimal volumeCm3,
                       @Param("weightKg") java.math.BigDecimal weightKg,
                       @Param("price") java.math.BigDecimal price,
                       @Param("postmanId") Long postmanId,
                       @Param("operatorId") Long operatorId);

    int reject(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /**
     * 推进到下一站：用 current_seq 做乐观条件，避免两个邮递员同时扫码把进度推两次。
     */
    int advance(@Param("orderNo") String orderNo,
                @Param("seq") int seq,
                @Param("stationId") Long stationId,
                @Param("state") OrderState state,
                @Param("prevSeq") Integer prevSeq);

    /**
     * 状态迁移：带 from 条件，避免并发下把已经变化的订单又改一次。
     * 返回 0 表示当时状态已不是 from。
     */
    int transition(@Param("orderNo") String orderNo,
                   @Param("from") OrderState from,
                   @Param("to") OrderState to);

    /**
     * 写入实测体积、质量与最终运费。只允许待取件或派送中的订单改价。
     */
    int confirmPrice(@Param("orderNo") String orderNo,
                     @Param("volumeCm3") java.math.BigDecimal volumeCm3,
                     @Param("weightKg") java.math.BigDecimal weightKg,
                     @Param("price") java.math.BigDecimal price,
                     @Param("now") LocalDateTime now,
                     @Param("postmanId") Long postmanId);
}
