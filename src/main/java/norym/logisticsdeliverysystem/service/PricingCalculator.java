package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.config.PricingProperties;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;
import norym.logisticsdeliverysystem.exception.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 纯运费计算：体积重、计费重量进位、按里程分档计费。
 * 不访问数据库，便于单测。
 */
public class PricingCalculator
{
    private static final BigDecimal TEN = new BigDecimal("10");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    private final BigDecimal volumetricDivisor;
    private final List<PricingProperties.Tier> tiers;

    public PricingCalculator(PricingProperties properties)
    {
        if (properties == null || properties.getVolumetricDivisor() == null
                || properties.getVolumetricDivisor().compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new IllegalArgumentException("体积重除数必须大于 0");
        }
        if (properties.getTiers() == null || properties.getTiers().isEmpty())
        {
            throw new IllegalArgumentException("运费分档不能为空");
        }
        this.volumetricDivisor = properties.getVolumetricDivisor();
        this.tiers = new ArrayList<>(properties.getTiers());
        this.tiers.sort(Comparator.comparing(tier -> tier.getMaxKm() == null
                ? new BigDecimal("999999999")
                : tier.getMaxKm()));
    }

    public PriceQuoteView calculate(BigDecimal weightKg, BigDecimal volumeCm3, BigDecimal distanceKm)
    {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ApiException(400, "里程不能为空或为负");
        }

        BigDecimal volumetricWeight = volumeCm3.divide(volumetricDivisor, 4, RoundingMode.HALF_UP);
        BigDecimal rawChargeable = weightKg.max(volumetricWeight);
        BigDecimal chargeableWeight = roundChargeableWeight(rawChargeable);
        PricingProperties.Tier tier = selectTier(distanceKm);
        BigDecimal additionalWeight = chargeableWeight.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal freight = tier.getFirstKgPrice()
                .add(additionalWeight.multiply(tier.getAdditionalKgPrice()))
                .setScale(0, RoundingMode.HALF_UP);

        return new PriceQuoteView(
                weightKg,
                volumeCm3,
                volumetricWeight,
                chargeableWeight,
                distanceKm,
                tier.getFirstKgPrice(),
                tier.getAdditionalKgPrice(),
                additionalWeight,
                freight,
                null,
                null,
                null);
    }

    /**
     * 顺丰内地互寄进位：不足 10kg 按 0.1kg 向上；10kg 到 100kg 以下按 0.5kg 向上；
     * 100kg 及以上四舍五入到整千克。不足 1kg 按 1kg 首重。
     */
    public static BigDecimal roundChargeableWeight(BigDecimal raw)
    {
        if (raw == null || raw.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ApiException(400, "计费重量不能为负");
        }
        BigDecimal rounded;
        if (raw.compareTo(TEN) < 0)
        {
            rounded = raw.multiply(TEN).setScale(0, RoundingMode.CEILING)
                    .divide(TEN, 1, RoundingMode.UNNECESSARY);
        }
        else if (raw.compareTo(HUNDRED) < 0)
        {
            rounded = raw.multiply(TWO).setScale(0, RoundingMode.CEILING)
                    .divide(TWO, 1, RoundingMode.UNNECESSARY);
        }
        else
        {
            rounded = raw.setScale(0, RoundingMode.HALF_UP);
        }
        if (rounded.compareTo(BigDecimal.ONE) < 0)
        {
            return BigDecimal.ONE;
        }
        return rounded;
    }

    private PricingProperties.Tier selectTier(BigDecimal distanceKm)
    {
        for (PricingProperties.Tier tier : tiers)
        {
            if (tier.getMaxKm() == null || distanceKm.compareTo(tier.getMaxKm()) <= 0)
            {
                return tier;
            }
        }
        throw new ApiException(500, "未找到匹配的运费分档");
    }
}
