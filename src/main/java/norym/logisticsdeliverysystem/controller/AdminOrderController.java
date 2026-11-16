package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.auth.AuthService;
import norym.logisticsdeliverysystem.auth.Role;
import norym.logisticsdeliverysystem.auth.SessionUser;
import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.ApproveRequest;
import norym.logisticsdeliverysystem.dto.AssignCourierRequest;
import norym.logisticsdeliverysystem.dto.AssignTripRequest;
import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.RejectRequest;
import norym.logisticsdeliverysystem.dto.RoutePlanView;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.service.AdminOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

/**
 * 后台管理端接口。身份一律从会话取，客户端不再能指定"我是谁"。
 * <p>
 * 路径管理员负责审批；节点管理员负责本站的车次登记、发运、到货确认，
 * 以及目的站的最后一公里派单。
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
    public ApiResult<List<OrderBriefView>> pending(
            @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireRole(user, Role.PATH_ADMIN);
        return ApiResult.ok(adminOrderService.pending());
    }

    /** 系统推荐的最短路线 */
    @GetMapping("/{orderNo}/plan")
    public ApiResult<RoutePlanView> plan(@PathVariable("orderNo") String orderNo,
                                         @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireRole(user, Role.PATH_ADMIN);
        return ApiResult.ok(adminOrderService.plan(orderNo));
    }

    /** 批准（可指定途经节点，不传则用最短路径） */
    @PostMapping("/{orderNo}/approve")
    public ApiResult<Void> approve(@PathVariable("orderNo") String orderNo,
                                   @RequestBody(required = false) ApproveRequest request,
                                   @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireRole(user, Role.PATH_ADMIN);
        adminOrderService.approve(orderNo, user.id(), request == null ? null : request.stationIds());
        return ApiResult.ok();
    }

    @PostMapping("/{orderNo}/reject")
    public ApiResult<Void> reject(@PathVariable("orderNo") String orderNo,
                                  @RequestBody(required = false) RejectRequest request,
                                  @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireRole(user, Role.PATH_ADMIN);
        adminOrderService.reject(orderNo, request == null ? null : request.reason());
        return ApiResult.ok();
    }

    /** 中转段指派车次 */
    @PostMapping("/{orderNo}/trip")
    public ApiResult<Void> assignTrip(@PathVariable("orderNo") String orderNo,
                                      @RequestBody AssignTripRequest request,
                                      @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireRole(user, Role.STATION_ADMIN);
        adminOrderService.assignTrip(orderNo, user.stationId(), request == null ? null : request.tripNo());
        return ApiResult.ok();
    }

    /** 最后一公里指派快递员（仅目的站） */
    @PostMapping("/{orderNo}/courier")
    public ApiResult<Void> assignCourier(@PathVariable("orderNo") String orderNo,
                                         @RequestBody AssignCourierRequest request,
                                         @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireRole(user, Role.STATION_ADMIN);
        adminOrderService.assignCourier(orderNo, user.stationId(), request == null ? null : request.postmanId());
        return ApiResult.ok();
    }

    /** 本站发运：货物交给车次带走 */
    @PostMapping("/{orderNo}/depart")
    public ApiResult<Void> depart(@PathVariable("orderNo") String orderNo,
                                  @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireRole(user, Role.STATION_ADMIN);
        adminOrderService.depart(orderNo, user.stationId());
        return ApiResult.ok();
    }

    /** 本站确认到货 */
    @PostMapping("/{orderNo}/arrive")
    public ApiResult<Void> arrive(@PathVariable("orderNo") String orderNo,
                                  @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireRole(user, Role.STATION_ADMIN);
        adminOrderService.arrive(orderNo, user.stationId());
        return ApiResult.ok();
    }

    private void requireRole(SessionUser user, Role expected)
    {
        if (user == null)
        {
            throw new ApiException(401, "请先登录");
        }
        if (user.role() != expected)
        {
            throw new ApiException(403, expected == Role.PATH_ADMIN
                    ? "该操作仅限路径管理员"
                    : "该操作仅限节点管理员");
        }
    }
}
