package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.RoutePlanView;

import java.util.List;

/**
 * 后台管理端。
 * <p>
 * 责任划分：中转段由车次承运，节点管理员只登记车次并做站间交接；
 * 最后一公里才指派快递员。
 */
public interface AdminOrderService
{
    /** 待审批请求 */
    List<OrderBriefView> pending();

    /** 系统推荐的最短路线，供批准前参考 */
    RoutePlanView plan(String orderNo);

    /** 批准并固化路线；stationIds 为空时自动取最短路径 */
    void approve(String orderNo, Long operatorId, List<Long> stationIds);

    void reject(String orderNo, String reason);

    /** 为中转段指派车次（不涉及快递员） */
    void assignTrip(String orderNo, Long operatorStationId, String tripNo);

    /** 为最后一公里指派快递员，必须是目的站的人 */
    void assignCourier(String orderNo, Long operatorStationId, Long postmanId);

    /** 本站发运：确认货物由指定车次带走 */
    void depart(String orderNo, Long operatorStationId);

    /** 目的站确认到货 */
    void arrive(String orderNo, Long operatorStationId);
}
