package norym.logisticsdeliverysystem.entity;

/**
 * 订单状态。
 * <p>
 * 流转关系：
 * WAITING --批准--> APPROVED --起点站发运--> SENDING --快递员取件--> DELIVERING
 * --签收--> RECEIVED（终态）
 * --拒收--> REFUSED（终态），同时生成一张方向相反的退回单
 * WAITING --管理员拒绝或审批超时--> REJECTED --用户修改重提--> WAITING
 * <p>
 * 注意：REJECTED 不是终态。拒收也不是"退回中"——退回由独立订单承载，
 * 原单在拒收那一刻就结束了。
 */
public enum OrderState
{
    WAITING,    // 待审批
    APPROVED,   // 已批准，等待发运
    REJECTED,   // 已拒绝（管理员拒绝或审批超时），可修改重提
    SENDING,    // 运送中；也包含"已到目的站、等快递员取件"
    DELIVERING, // 派送中（快递员已取件）
    RECEIVED,   // 已签收
    REFUSED     // 已拒收
}
