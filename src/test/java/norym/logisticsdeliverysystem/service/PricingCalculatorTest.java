package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.config.PricingProperties;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingCalculatorTest
{
    private final PricingCalculator calculator = new PricingCalculator(PricingProperties.defaults());

    @Test
    void roundsChargeableWeightBySfInlandRules()
    {
        // 0.01 先按 0.1kg 向上进位，仍不足 1kg，按首重记 1kg
        assertEquals(0, dec("1").compareTo(bumped("0.01")));
        assertEquals(0, dec("1").compareTo(bumped("0.9")));
        assertEquals(0, dec("1.0").compareTo(bumped("1")));
        assertEquals(0, dec("2.4").compareTo(bumped("2.4")));
        assertEquals(0, dec("9.1").compareTo(bumped("9.01")));
        assertEquals(0, dec("10.0").compareTo(bumped("10")));
        assertEquals(0, dec("10.5").compareTo(bumped("10.01")));
        assertEquals(0, dec("100.0").compareTo(bumped("99.51")));
        assertEquals(0, dec("100").compareTo(bumped("100")));
        assertEquals(0, dec("100").compareTo(bumped("100.4")));
        assertEquals(0, dec("101").compareTo(bumped("100.5")));
    }

    @Test
    void beijingToHangzhouExampleIs37Yuan()
    {
        PriceQuoteView quote = calculator.calculate(dec("2.4"), dec("12000"), dec("1357"));
        assertEquals(0, dec("2.0000").compareTo(quote.volumetricWeightKg()));
        assertEquals(0, dec("2.4").compareTo(quote.chargeableWeightKg()));
        assertEquals(0, dec("1357").compareTo(quote.distanceKm()));
        assertEquals(0, dec("23").compareTo(quote.firstKgPrice()));
        assertEquals(0, dec("10").compareTo(quote.additionalKgPrice()));
        assertEquals(0, dec("1.4").compareTo(quote.additionalWeightKg()));
        assertEquals(0, dec("37").compareTo(quote.freight()));
    }

    @Test
    void shanghaiToHangzhouOneKilogramIs18Yuan()
    {
        PriceQuoteView quote = calculator.calculate(dec("1"), dec("1000"), dec("175"));
        assertEquals(0, dec("0.1667").compareTo(quote.volumetricWeightKg()));
        assertEquals(0, dec("1").compareTo(quote.chargeableWeightKg()));
        assertEquals(0, dec("175").compareTo(quote.distanceKm()));
        assertEquals(0, dec("18").compareTo(quote.firstKgPrice()));
        assertEquals(0, dec("5").compareTo(quote.additionalKgPrice()));
        assertEquals(0, dec("0").compareTo(quote.additionalWeightKg()));
        assertEquals(0, dec("18").compareTo(quote.freight()));
    }

    @Test
    void mileageTiersUseInclusiveBoundsAt200And1000()
    {
        assertEquals(0, dec("18").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("200")).firstKgPrice()));
        assertEquals(0, dec("5").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("200")).additionalKgPrice()));
        assertEquals(0, dec("18").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("200")).freight()));

        assertEquals(0, dec("22").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("200.01")).firstKgPrice()));
        assertEquals(0, dec("8").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("200.01")).additionalKgPrice()));

        assertEquals(0, dec("22").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("1000")).firstKgPrice()));
        assertEquals(0, dec("8").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("1000")).additionalKgPrice()));
        assertEquals(0, dec("22").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("1000")).freight()));

        assertEquals(0, dec("23").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("1000.01")).firstKgPrice()));
        assertEquals(0, dec("10").compareTo(calculator.calculate(dec("1"), dec("1000"), dec("1000.01")).additionalKgPrice()));
    }

    private static BigDecimal bumped(String raw)
    {
        return PricingCalculator.roundChargeableWeight(dec(raw));
    }

    private static BigDecimal dec(String value)
    {
        return new BigDecimal(value);
    }
}
