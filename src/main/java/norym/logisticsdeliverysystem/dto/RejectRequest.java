package norym.logisticsdeliverysystem.dto;

/**
 * 拒绝订单的请求体。操作人从会话取。
 */
public record RejectRequest(String reason)
{
}
