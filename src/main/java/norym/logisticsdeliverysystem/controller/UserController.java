package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.CreatedOrderView;
import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.OrderDetailView;
import norym.logisticsdeliverysystem.dto.OrderFormRequest;
import norym.logisticsdeliverysystem.dto.OrderStatusView;
import norym.logisticsdeliverysystem.dto.ReceiverActionRequest;
import norym.logisticsdeliverysystem.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端接口。用户不登录，凭手机号与单号操作。
 */
@RestController
@RequestMapping("/api/user/orders")
public class UserController
{
    private final OrderService orderService;

    public UserController(OrderService orderService)
    {
        this.orderService = orderService;
    }

    /** 发起订单，返回邮件编号供用户查询审批结果 */
    @PostMapping
    public ApiResult<CreatedOrderView> create(@RequestBody OrderFormRequest request)
    {
        return ApiResult.ok(orderService.create(request));
    }

    /** 用手机号获取所有相关单号（寄件人与收件人都匹配） */
    @GetMapping
    public ApiResult<List<OrderBriefView>> listByPhone(@RequestParam("phone") String phone)
    {
        return ApiResult.ok(orderService.listByPhone(phone));
    }

    /** 用邮件编号获取详细信息 */
    @GetMapping("/{orderNo}")
    public ApiResult<OrderDetailView> detail(@PathVariable("orderNo") String orderNo)
    {
        return ApiResult.ok(orderService.detail(orderNo));
    }

    /** 查询订单是否通过审批（前端轮询用，返回体很小） */
    @GetMapping("/{orderNo}/status")
    public ApiResult<OrderStatusView> status(@PathVariable("orderNo") String orderNo)
    {
        return ApiResult.ok(orderService.status(orderNo));
    }

    /** 签收：收件人手机号 + 邮件编号确认 */
    @PostMapping("/{orderNo}/sign")
    public ApiResult<Void> sign(@PathVariable("orderNo") String orderNo,
                                @RequestBody ReceiverActionRequest request)
    {
        orderService.sign(orderNo, request == null ? null : request.receiverPhone());
        return ApiResult.ok();
    }

    /** 拒收：原单终结，同时生成一张退回单，返回退回单号供查询 */
    @PostMapping("/{orderNo}/refuse")
    public ApiResult<norym.logisticsdeliverysystem.dto.CreatedOrderView> refuse(
            @PathVariable("orderNo") String orderNo,
            @RequestBody ReceiverActionRequest request)
    {
        return ApiResult.ok(orderService.refuse(orderNo,
                request == null ? null : request.receiverPhone(),
                request == null ? null : request.reason()));
    }

    /** 被拒后修改信息重提 */
    @PutMapping("/{orderNo}")
    public ApiResult<Void> resubmit(@PathVariable("orderNo") String orderNo,
                                    @RequestBody OrderFormRequest request)
    {
        orderService.resubmit(orderNo, request);
        return ApiResult.ok();
    }

    /** 二维码内容：服务器只出字符串，图片由前端画 */
    @GetMapping("/{orderNo}/qrcode")
    public ApiResult<norym.logisticsdeliverysystem.dto.QrCodeView> qrCode(@PathVariable("orderNo") String orderNo)
    {
        return ApiResult.ok(orderService.qrCode(orderNo));
    }
}
