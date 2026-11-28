package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.DeliverRequest;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;

import java.util.List;

/**
 * 快递员端：可参与始发站收件核价，并负责最后一公里。
 * 中转运输由车次承运，快递员不参与。
 */
public interface PostmanOrderService
{
    /**
     * 我的任务。
     *
     * @param stationId 快递员所属站，用于补充“始发站待收件”任务
     * @param type      pending 手里待办（始发收件 / 货已到站等取件 / 派送中）；completed 已完成
     */
    List<PostmanTask> tasks(Long postmanId, Long stationId, String type);

    /**
     * 按单号定位本人的待办任务：最后一公里待取/派送，或本站始发待收件。
     * 不是自己的单返回 403，已结束的单返回 409。
     */
    PostmanTask taskByOrderNo(String orderNo, Long postmanId, Long stationId);

    /**
     * 只读复核最终运费：最终价已在始发站收件时锁定，本方法不再写库改价。
     * 仅该单指派的快递员可调；尚未核价时返回 409。
     */
    PriceQuoteView confirmPrice(String orderNo, Long postmanId, ConfirmPriceRequest request);

    /**
     * 取件开始派送：确认该单指派给了本人、货物已在目的站、运费已在始发站确认。
     * 取件后订单才进入 DELIVERING，随后由收件人签收或快递员妥投。
     */
    void pickup(String orderNo, Long postmanId, ConfirmPriceRequest request);

    /**
     * 妥投或派送失败。成功与用户端签收二选一，谁先完成谁生效；
     * 失败不生成退回单，订单回到目的站待派。
     */
    void deliver(String orderNo, Long postmanId, DeliverRequest request);
}
