package norym.logisticsdeliverysystem.entity;

/**
 * 订单状态。
 * <p>
 * 流转关系：
 * WAITING --批准--> APPROVED --起点站发运--> SENDING --到达目的站派送--> DELIVERING
 * --签收--> RECEIVED（终态）
 * --拒收--> RETURNING --回到始发站--> RETURNED（终态）
 * WAITING --管理员拒绝或审批超时--> REJECTED --用户修改重提--> WAITING
 * <p>
 * 注意：REJECTED 不是终态，拒收也不直接是终态——业务上货要原路退回。
 */
public enum OrderState
{
    WAITING,    // 待审批
    APPROVED,   // 已批准，等待发运
    REJECTED,   // 已拒绝（管理员拒绝或审批超时），可修改重提
    SENDING,    // 运送中
    DELIVERING, // 派送中
    RECEIVED,   // 已签收
    RETURNING,  // 退回中
    RETURNED    // 已退回
}
