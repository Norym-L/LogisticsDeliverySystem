package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;

/**
 * 发起订单 / 修改重提的请求体。
 * <p>
 * 只收用户能自己填的字段。路径、当前位置、状态、事件时间一律由服务器生成——
 * 如果允许客户端传这些，任何人都能提交一个 state=RECEIVED 的订单，
 * 审批和运输流程直接被绕过。
 */
public record OrderFormRequest(
        String senderName,
        String senderPhone,
        String receiverName,
        String receiverPhone,
        Long fromStationId,
        Long toStationId,
        String goodsDesc,
        BigDecimal weight,
        BigDecimal volumeCm3)
{
}
