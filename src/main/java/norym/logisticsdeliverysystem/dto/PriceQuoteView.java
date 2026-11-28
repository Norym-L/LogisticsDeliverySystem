package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运费试算 / 核价结果。
 * <p>
 * estimatedDays 为简易时效：路线每跨一站记 1 日，最少 1 日。
 * 下单试算用最短路，订单详情用已固化路线（批准改路线后会变化）。
 */
public record PriceQuoteView(
        BigDecimal weightKg,
        BigDecimal volumeCm3,
        BigDecimal volumetricWeightKg,
        BigDecimal chargeableWeightKg,
        BigDecimal distanceKm,
        BigDecimal firstKgPrice,
        BigDecimal additionalKgPrice,
        BigDecimal additionalWeightKg,
        BigDecimal freight,
        Integer estimatedDays,
        String orderNo,
        Boolean priceConfirmed,
        LocalDateTime priceConfirmedAt)
{
    public PriceQuoteView withConfirmation(String orderNo, LocalDateTime confirmedAt)
    {
        return new PriceQuoteView(
                weightKg,
                volumeCm3,
                volumetricWeightKg,
                chargeableWeightKg,
                distanceKm,
                firstKgPrice,
                additionalKgPrice,
                additionalWeightKg,
                freight,
                estimatedDays,
                orderNo,
                Boolean.TRUE,
                confirmedAt);
    }

    public PriceQuoteView withEstimatedDays(Integer days)
    {
        return new PriceQuoteView(
                weightKg,
                volumeCm3,
                volumetricWeightKg,
                chargeableWeightKg,
                distanceKm,
                firstKgPrice,
                additionalKgPrice,
                additionalWeightKg,
                freight,
                days,
                orderNo,
                priceConfirmed,
                priceConfirmedAt);
    }
}
