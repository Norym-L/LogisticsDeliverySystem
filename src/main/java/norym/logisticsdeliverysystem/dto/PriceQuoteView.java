package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运费试算 / 核价结果。
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
                orderNo,
                Boolean.TRUE,
                confirmedAt);
    }
}
