package norym.logisticsdeliverysystem.dto;

import java.time.LocalDateTime;

/**
 * 发起订单的返回：用户拿这个单号去轮询审批结果。
 */
public record CreatedOrderView(String orderNo, String state, LocalDateTime approveDeadline)
{
}
