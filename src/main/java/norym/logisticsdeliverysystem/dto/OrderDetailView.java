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
        String goodsDesc,
        BigDecimal weight,
        List<RouteNodeView> trajectory,
        Events events,
        String rejectReason,
        String refuseReason,
        String returnOrderNo)
{
    /**
     * 订单级事件时间：下单、批准、签收、拒收。
     * 每个站点的到站时间在 trajectory 里。
     */
    public record Events(
            LocalDateTime createdAt,
            LocalDateTime approvedAt,
            LocalDateTime receivedAt,
            LocalDateTime refusedAt)
    {
    }
}
