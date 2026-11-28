package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.auth.AuthService;
import norym.logisticsdeliverysystem.auth.Role;
import norym.logisticsdeliverysystem.auth.SessionUser;
import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.service.PostmanOrderService;
import norym.logisticsdeliverysystem.service.ReceiptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

/**
 * 快递员接口：始发站收件核价 + 最后一公里。
 * <p>
 * 中转运输由车次承运，快递员不参与，所以这里没有干线发运/到站的概念；
 * 只有本站始发收件、目的站核价复核、取件、妥投/失败。
 */
@RestController
@RequestMapping("/api/postman/orders")
public class PostmanController
{
    private final PostmanOrderService postmanOrderService;
    private final ReceiptService receiptService;

    public PostmanController(PostmanOrderService postmanOrderService, ReceiptService receiptService)
    {
        this.postmanOrderService = postmanOrderService;
        this.receiptService = receiptService;
    }

    /** 我的任务：登录后先看这个，再去取件 */
    @GetMapping
    public ApiResult<List<PostmanTask>> tasks(@RequestParam(value = "type", required = false) String type,
                                              @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requirePostman(user);
        return ApiResult.ok(postmanOrderService.tasks(user.id(), user.stationId(), type));
    }

    /** 手工录入/扫码定位：只返回本人的待取、派送中或本站待收件任务 */
    @GetMapping("/{orderNo}")
    public ApiResult<PostmanTask> task(@PathVariable("orderNo") String orderNo,
                                       @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requirePostman(user);
        return ApiResult.ok(postmanOrderService.taskByOrderNo(orderNo, user.id(), user.stationId()));
    }

    /**
     * 始发站快递员确认收件并核价：与节点管理员的 /admin/orders/{no}/receive 等价，
     * 只能操作本站始发、且处于 AWAITING_RECEIPT 的订单。
     */
    @PostMapping("/{orderNo}/receive")
    public ApiResult<PriceQuoteView> receive(@PathVariable("orderNo") String orderNo,
                                             @RequestBody(required = false) ConfirmPriceRequest request,
                                             @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requirePostman(user);
        return ApiResult.ok(receiptService.confirmReceipt(
                orderNo, user.stationId(), null, user.id(), request));
    }

    /** 只读复核最终运费：最终价已在始发站收件时锁定，不再改价 */
    @PostMapping("/{orderNo}/price")
    public ApiResult<PriceQuoteView> confirmPrice(@PathVariable("orderNo") String orderNo,
                                                  @RequestBody(required = false) ConfirmPriceRequest request,
                                                  @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requirePostman(user);
        return ApiResult.ok(postmanOrderService.confirmPrice(orderNo, user.id(), request));
    }

    /** 扫码取件，开始派送。运费须已在始发站确认 */
    @PostMapping("/{orderNo}/pickup")
    public ApiResult<Void> pickup(@PathVariable("orderNo") String orderNo,
                                  @RequestBody(required = false) ConfirmPriceRequest request,
                                  @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requirePostman(user);
        postmanOrderService.pickup(orderNo, user.id(), request);
        return ApiResult.ok();
    }

    /** 妥投（本人签收/电话确认）或派送失败（联系不上/地址有误） */
    @PostMapping("/{orderNo}/deliver")
    public ApiResult<Void> deliver(@PathVariable("orderNo") String orderNo,
                                   @RequestBody(required = false) norym.logisticsdeliverysystem.dto.DeliverRequest request,
                                   @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requirePostman(user);
        postmanOrderService.deliver(orderNo, user.id(), request);
        return ApiResult.ok();
    }

    private void requirePostman(SessionUser user)
    {
        if (user == null)
        {
            throw new ApiException(401, "请先登录");
        }
        if (user.role() != Role.POSTMAN)
        {
            throw new ApiException(403, "该接口仅限快递员");
        }
    }
}
