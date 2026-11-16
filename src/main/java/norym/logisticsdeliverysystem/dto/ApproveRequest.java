package norym.logisticsdeliverysystem.dto;

import java.util.List;

/**
 * 批准订单的请求体。
 *
 * @param operatorId 路径管理员 id（Session 上线后改从会话取）
 * @param stationIds 途经节点，必须含始发站与目的站；为空时由服务器按最短路径自动给出
 */
public record ApproveRequest(Long operatorId, List<Long> stationIds)
{
}
