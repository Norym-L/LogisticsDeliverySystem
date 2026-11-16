package norym.logisticsdeliverysystem.dto;

/**
 * 邮递员扫码动作的请求体。扫码得到的只是订单号，动作由接口路径区分。
 */
public record PostmanActionRequest(Long postmanId)
{
}
