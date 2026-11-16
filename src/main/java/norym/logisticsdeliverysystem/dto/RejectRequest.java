package norym.logisticsdeliverysystem.dto;

/**
 * 拒绝订单的请求体。
 */
public record RejectRequest(Long operatorId, String reason)
{
}
