package norym.logisticsdeliverysystem.dto;

import java.time.LocalDateTime;

/**
 * 审批状态查询结果，供前端轮询使用。
 * 返回体刻意做小：只带状态、拒绝原因和剩余等待时间。
 *
 * @param remainingSeconds 距审批截止还剩多少秒；非 WAITING 状态为 0
 */
public record OrderStatusView(
        String orderNo,
        String state,
        String rejectReason,
        LocalDateTime approveDeadline,
        long remainingSeconds)
{
}
