package norym.logisticsdeliverysystem.dto;

/**
 * 用户取消未发运订单：用寄件人手机号确认身份。
 */
public record CancelOrderRequest(String senderPhone)
{
}
