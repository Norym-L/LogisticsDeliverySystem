package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.PriceQuoteRequest;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;

import java.math.BigDecimal;

/**
 * 运费试算与里程解析。
 */
public interface PricingService
{
    /** 公开试算，不写库 */
    PriceQuoteView quote(PriceQuoteRequest request);

    /** 用最短路里程计算，供下单预估 */
    PriceQuoteView quoteShortest(Long fromStationId, Long toStationId,
                                 BigDecimal weightKg, BigDecimal volumeCm3);

    /** 按公式计算，里程已确定 */
    PriceQuoteView calculate(BigDecimal weightKg, BigDecimal volumeCm3, BigDecimal distanceKm);

    /** 已批准且有固化路线时用实际里程，否则用最短路 */
    BigDecimal distanceForOrder(DeliveryOrder order);

    /** 质量和体积必须大于 0，且不超过上限 */
    void validateMeasures(BigDecimal weightKg, BigDecimal volumeCm3);
}
