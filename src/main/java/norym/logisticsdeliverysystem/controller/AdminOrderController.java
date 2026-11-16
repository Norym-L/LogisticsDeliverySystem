package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.ApproveRequest;
import norym.logisticsdeliverysystem.dto.AssignRequest;
import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.RejectRequest;
import norym.logisticsdeliverysystem.dto.RoutePlanView;
import norym.logisticsdeliverysystem.service.AdminOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台管理端接口。
 * <p>
 * 目前 operatorId 由请求体显式传入，Session 鉴权接上后改为从会话取，
 * 届时这些参数会删掉。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController
{
    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService)
    {
        this.adminOrderService = adminOrderService;
    }

    /** 检查请求：待审批订单列表 */
    @GetMapping("/pending")
    public ApiResult<List<OrderBriefView>> pending()
    {
        return ApiResult.ok(adminOrderService.pending());
    }

    /** 系统推荐的最短路线 */
    @GetMapping("/{orderNo}/plan")
    public ApiResult<RoutePlanView> plan(@PathVariable("orderNo") String orderNo)
    {
        return ApiResult.ok(adminOrderService.plan(orderNo));
    }

    /** 批准（可指定途经节点，不传则用最短路径） */
    @PostMapping("/{orderNo}/approve")
    public ApiResult<Void> approve(@PathVariable("orderNo") String orderNo,
                                   @RequestBody(required = false) ApproveRequest request)
    {
        adminOrderService.approve(orderNo, request);
        return ApiResult.ok();
    }

    @PostMapping("/{orderNo}/reject")
    public ApiResult<Void> reject(@PathVariable("orderNo") String orderNo,
                                  @RequestBody(required = false) RejectRequest request)
    {
        adminOrderService.reject(orderNo, request == null ? null : request.reason());
        return ApiResult.ok();
    }

    /** 节点管理员为当前路段指派邮递员与车次 */
    @PostMapping("/{orderNo}/assign")
    public ApiResult<Void> assign(@PathVariable("orderNo") String orderNo,
                                  @RequestBody AssignRequest request)
    {
        adminOrderService.assign(orderNo, request);
        return ApiResult.ok();
    }
}
