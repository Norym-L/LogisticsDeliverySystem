package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.ApproveRequest;
import norym.logisticsdeliverysystem.dto.AssignRequest;
import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.RoutePlanView;

import java.util.List;

/**
 * 后台管理端：路径管理员审批，节点管理员指派运力。
 */
public interface AdminOrderService
{
    /** 待审批请求 */
    List<OrderBriefView> pending();

    /** 系统推荐的最短路线，供批准前参考 */
    RoutePlanView plan(String orderNo);

    /** 批准并固化路线；stationIds 为空时自动取最短路径 */
    void approve(String orderNo, ApproveRequest request);

    void reject(String orderNo, String reason);

    /** 为当前路段指派承运邮递员与车次 */
    void assign(String orderNo, AssignRequest request);
}
