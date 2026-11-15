package norym.logisticsdeliverysystem.dto;

import java.time.LocalDateTime;

/**
 * 订单列表项：按手机号查到的一行。
 * 只给单号、状态、起止站和下单时间——用户拿到单号再去查详情。
 */
public record OrderBriefView(
        String orderNo,
        String state,
        String fromStation,
        String toStation,
        LocalDateTime createTime)
{
}
