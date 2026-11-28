package norym.logisticsdeliverysystem.entity;

/**
 * 订单状态。
 * <p>
 * 流转关系：
 * WAITING --批准--> AWAITING_RECEIPT --始发站确认收件并核价--> APPROVED
 * --起点站首次发运--> SENDING --快递员取件--> DELIVERING
 * --签收--> RECEIVED（终态）
 * --拒收--> REFUSED（终态），同时生成一张方向相反的退回单
 * WAITING --管理员拒绝或审批超时--> REJECTED --用户修改重提--> WAITING
 * WAITING / AWAITING_RECEIPT / APPROVED --用户取消--> CANCELLED（终态）
 * <p>
 * 注意：REJECTED 不是终态。拒收也不是"退回中"——退回由独立订单承载，
 * 原单在拒收那一刻就结束了。
 */
public enum OrderState
{
    WAITING,            // 待审批
    AWAITING_RECEIPT,   // 已批准，等待始发站确认收件（未发运）
    APPROVED,           // 始发站已收件并核价，等待首次发运
    REJECTED,           // 已拒绝（管理员拒绝或审批超时），可修改重提
    SENDING,            // 运送中；含"已到目的站等取件"与"派送失败待再派"
    DELIVERING,         // 派送中（快递员已取件）
    RECEIVED,           // 已签收
    REFUSED,            // 已拒收
    CANCELLED           // 已取消（仅首次发运前）
}
