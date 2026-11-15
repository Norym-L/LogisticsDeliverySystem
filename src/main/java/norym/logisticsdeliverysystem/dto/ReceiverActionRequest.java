package norym.logisticsdeliverysystem.dto;

/**
 * 签收 / 拒收的请求体：收件人手机号用于身份确认，拒收时可附原因。
 */
public record ReceiverActionRequest(String receiverPhone, String reason)
{
}
