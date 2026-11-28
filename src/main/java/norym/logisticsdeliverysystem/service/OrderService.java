package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.CreatedOrderView;
import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.OrderDetailView;
import norym.logisticsdeliverysystem.dto.OrderFormRequest;
import norym.logisticsdeliverysystem.dto.OrderStatusView;
import norym.logisticsdeliverysystem.dto.QrCodeView;

import java.util.List;

public interface OrderService
{
    /** 发起订单，返回邮件编号 */
    CreatedOrderView create(OrderFormRequest request);

    /** 用手机号查所有相关单号（寄件人与收件人都匹配） */
    List<OrderBriefView> listByPhone(String phone);

    /** 用单号查详情 */
    OrderDetailView detail(String orderNo);

    /** 查询是否通过审批（前端轮询用） */
    OrderStatusView status(String orderNo);

    /** 收件人签收 */
    void sign(String orderNo, String receiverPhone);

    /** 收件人拒收：原单终结，同时生成一张走同样审批流程的退回单 */
    CreatedOrderView refuse(String orderNo, String receiverPhone, String reason);

    /**
     * 寄件人取消：仅首次发运前（WAITING / 待始发收件 / 已收件待发运）可取消，
     * 用寄件人手机号确认身份。
     */
    void cancel(String orderNo, String senderPhone);

    /** 被拒后修改信息重提 */
    void resubmit(String orderNo, OrderFormRequest request);

    /** 二维码内容（服务器出内容，前端画图） */
    QrCodeView qrCode(String orderNo);
}
