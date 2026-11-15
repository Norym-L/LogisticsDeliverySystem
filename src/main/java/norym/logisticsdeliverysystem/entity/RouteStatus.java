package norym.logisticsdeliverysystem.entity;

/**
 * 订单路线中单个路段的状态。
 */
public enum RouteStatus
{
    PENDING,    // 未到达
    ARRIVED,    // 已到站
    DELIVERING, // 派送中（仅去程末段）
    SIGNED,     // 已签收（仅去程末段）
    REFUSED     // 已拒收（仅去程末段）
}
