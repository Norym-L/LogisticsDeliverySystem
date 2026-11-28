package norym.logisticsdeliverysystem.dto;

/**
 * 快递员妥投/派送失败。
 * <p>
 * outcome=DELIVERED 时 method 取 SELF（本人签收）或 PHONE（电话确认）；
 * outcome=FAILED 时 reason 取 UNREACHABLE（联系不上）或 WRONG_ADDRESS（地址有误）。
 * 失败不会生成退回单，订单回到目的站待派，由站点重新指派。
 */
public record DeliverRequest(String outcome, String method, String reason)
{
}
