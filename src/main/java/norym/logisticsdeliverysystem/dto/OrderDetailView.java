package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情，对应需求里的"邮件详细信息"。
 */
public record OrderDetailView(
        String orderNo,
        String state,
        String senderName,
        String senderPhone,
        String receiverName,
        String receiverPhone,
        StationView fromStation,
        StationView toStation,
        StationView currentStation,
        /** 简易预计时效：路线每跨一站 1 日，最少 1 日；批准改路线后会变化 */
        Integer estimatedDays,
        /** 预计送达时间 = 下单时间 + estimatedDays 天（简化规则，不接外部预测） */
        LocalDateTime estimatedDeliveryAt,
        String goodsDesc,
        BigDecimal weight,
        BigDecimal volumeCm3,
        BigDecimal actualWeight,
        BigDecimal actualVolumeCm3,
        BigDecimal quotedPrice,
        BigDecimal price,
        Boolean priceConfirmed,
        LocalDateTime priceConfirmedAt,
        List<RouteNodeView> trajectory,
        Events events,
        String rejectReason,
        String refuseReason,
        String returnOrderNo,
        /** 本单若是退回单，指向原单号；普通订单为空 */
        String originalOrderNo)
{
    /**
     * 订单级事件时间：下单、批准、始发站收件、签收、拒收、取消。
     * 每个站点的到站时间在 trajectory 里。
     */
    public record Events(
            LocalDateTime createdAt,
            LocalDateTime approvedAt,
            LocalDateTime originReceivedAt,
            LocalDateTime receivedAt,
            LocalDateTime refusedAt,
            LocalDateTime cancelledAt)
    {
    }
}
