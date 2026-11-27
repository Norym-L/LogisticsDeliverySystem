package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;

/**
 * 运费试算请求。体积和质量以本次传入为准；
 * 里程由起止站最短路，或已有订单的固化路线决定。
 */
public record PriceQuoteRequest(
        BigDecimal weightKg,
        BigDecimal volumeCm3,
        Long fromStationId,
        Long toStationId,
        String orderNo)
{
}
