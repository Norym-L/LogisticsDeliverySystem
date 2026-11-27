package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;

/**
 * 快递员核价，以及取件时附带的实测体积与质量。
 * 价格由服务器按同一公式计算，不接受客户端传入的运费。
 */
public record ConfirmPriceRequest(
        BigDecimal volumeCm3,
        BigDecimal weightKg)
{
}
