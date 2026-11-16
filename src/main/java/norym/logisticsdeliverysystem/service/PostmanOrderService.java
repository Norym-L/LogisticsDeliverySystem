package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.PostmanTask;

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
     * 取件开始派送：确认该单指派给了本人、货物已在目的站。
     * 取件后订单才进入 DELIVERING，随后由收件人签收。
     */
    void pickup(String orderNo, Long postmanId);
}
