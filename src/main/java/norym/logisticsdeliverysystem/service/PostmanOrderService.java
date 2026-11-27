package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;

import java.util.List;

/**
 * 快递员端。快递员只负责最后一公里，不参与中转运输。
 */
public interface PostmanOrderService
{
    /**
     * 我的任务。
     *
     * @param type pending 手里待送（货已到站等取件 / 派送中）；completed 已完成
     */
    List<PostmanTask> tasks(Long postmanId, String type);

    /**
     * 核价：用实测体积和质量按同一公式计算最终运费并写入。
     * 仅该单指派的快递员可调，状态须为待取件或派送中；签收/拒收前可重复核对。
     */
    PriceQuoteView confirmPrice(String orderNo, Long postmanId, ConfirmPriceRequest request);

    /**
     * 取件开始派送：确认该单指派给了本人、货物已在目的站。
     * 尚未核价时，请求体须附带实测体积和质量，在同一事务里先核价再取件。
     * 取件后订单才进入 DELIVERING，随后由收件人签收。
     */
    void pickup(String orderNo, Long postmanId, ConfirmPriceRequest request);
}
