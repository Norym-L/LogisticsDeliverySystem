package norym.logisticsdeliverysystem.service;

/**
 * 邮递员扫码操作：扫同一个二维码，动作由接口区分。
 */
public interface PostmanOrderService
{
    /** 扫码开始送货：确认本段指派给自己，登记发运时间 */
    void depart(String orderNo, Long postmanId);

    /** 扫码确认到下一站：推进路线进度，到末站自动转派送中 */
    void arrive(String orderNo, Long postmanId);
}
