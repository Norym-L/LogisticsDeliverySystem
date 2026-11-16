package norym.logisticsdeliverysystem.dto;

import java.util.List;

/**
 * 批准订单的请求体。操作人从会话取，不再由客户端传。
 *
 * @param stationIds 途经节点，必须含始发站与目的站；为空时由服务器按最短路径自动给出
 */
public record ApproveRequest(List<Long> stationIds)
{
}
