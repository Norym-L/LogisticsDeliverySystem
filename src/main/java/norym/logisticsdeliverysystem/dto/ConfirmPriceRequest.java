package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;

/**
 * 实测体积与质量。用于始发站确认收件时的最终核价；
 * 目的站取件接口保留该入参但不再据此改价。
 * 价格由服务器按计价公式计算，不接受客户端传入的运费。
 */
public record ConfirmPriceRequest(
        BigDecimal volumeCm3,
        BigDecimal weightKg)
{
}
