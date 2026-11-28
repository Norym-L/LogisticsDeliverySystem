package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;

/**
 * 始发站收件确认与最终核价。
 * <p>
 * 路径管理员批准后订单处于 AWAITING_RECEIPT；始发站管理员或本站快递员
 * 确认收件时，录入/核对体积和质量，系统按已固化路线计算最终运费。
 * 这是全流程唯一一次最终核价，发运前必须完成。
 */
public interface ReceiptService
{
    /**
     * @param stationId  操作人所属站（节点管理员与快递员都有）
     * @param operatorId 节点管理员 id，与 postmanId 二选一
     * @param postmanId  快递员 id，与 operatorId 二选一
     */
    PriceQuoteView confirmReceipt(String orderNo, Long stationId, Long operatorId,
                                  Long postmanId, ConfirmPriceRequest request);
}
