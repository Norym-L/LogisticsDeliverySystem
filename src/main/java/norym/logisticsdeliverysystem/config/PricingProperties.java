package norym.logisticsdeliverysystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 运费费率与体积重除数，对应 application.yaml 里的 logistics.pricing。
 * 分档可改配置，不必动代码。
 */
@ConfigurationProperties(prefix = "logistics.pricing")
public class PricingProperties
{
    private BigDecimal volumetricDivisor = new BigDecimal("6000");
    private List<Tier> tiers = defaultTiers();

    public static PricingProperties defaults()
    {
        PricingProperties properties = new PricingProperties();
        properties.setVolumetricDivisor(new BigDecimal("6000"));
        properties.setTiers(defaultTiers());
        return properties;
    }

    private static List<Tier> defaultTiers()
    {
        List<Tier> tiers = new ArrayList<>();
        tiers.add(tier(new BigDecimal("200"), new BigDecimal("18"), new BigDecimal("5")));
        tiers.add(tier(new BigDecimal("1000"), new BigDecimal("22"), new BigDecimal("8")));
        tiers.add(tier(null, new BigDecimal("23"), new BigDecimal("10")));
        return tiers;
    }

    private static Tier tier(BigDecimal maxKm, BigDecimal first, BigDecimal additional)
    {
        Tier tier = new Tier();
        tier.setMaxKm(maxKm);
        tier.setFirstKgPrice(first);
        tier.setAdditionalKgPrice(additional);
        return tier;
    }

    public BigDecimal getVolumetricDivisor()
    {
        return volumetricDivisor;
    }

    public void setVolumetricDivisor(BigDecimal volumetricDivisor)
    {
        this.volumetricDivisor = volumetricDivisor;
    }

    public List<Tier> getTiers()
    {
        return tiers;
    }

    public void setTiers(List<Tier> tiers)
    {
        this.tiers = tiers;
    }

    public static class Tier
    {
        /** 该档上限（含）；为空表示没有上限 */
        private BigDecimal maxKm;
        private BigDecimal firstKgPrice;
        private BigDecimal additionalKgPrice;

        public BigDecimal getMaxKm()
        {
            return maxKm;
        }

        public void setMaxKm(BigDecimal maxKm)
        {
            this.maxKm = maxKm;
        }

        public BigDecimal getFirstKgPrice()
        {
            return firstKgPrice;
        }

        public void setFirstKgPrice(BigDecimal firstKgPrice)
        {
            this.firstKgPrice = firstKgPrice;
        }

        public BigDecimal getAdditionalKgPrice()
        {
            return additionalKgPrice;
        }

        public void setAdditionalKgPrice(BigDecimal additionalKgPrice)
        {
            this.additionalKgPrice = additionalKgPrice;
        }
    }
}
