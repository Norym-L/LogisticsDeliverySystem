package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.auth.AuthService;
import norym.logisticsdeliverysystem.auth.Role;
import norym.logisticsdeliverysystem.auth.SessionUser;
import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.PostmanInfo;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.service.AdminPostmanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

/**
 * 管理端查询配送员。两类管理员都能看，但节点管理员被限制在本站范围内。
 */
@RestController
@RequestMapping("/api/admin/postmen")
public class AdminPostmanController
{
    private final AdminPostmanService adminPostmanService;

    public AdminPostmanController(AdminPostmanService adminPostmanService)
    {
        this.adminPostmanService = adminPostmanService;
    }

    /**
     * 配送员列表，带手里单量统计。
     * 节点管理员传什么 stationId 都无效，强制改为自己所属站。
     */
    @GetMapping
    public ApiResult<List<PostmanInfo>> list(@RequestParam(value = "stationId", required = false) Long stationId,
                                             @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireAdmin(user);
        if (user.role() == Role.STATION_ADMIN)
        {
            stationId = user.stationId();
        }
        return ApiResult.ok(adminPostmanService.list(stationId));
    }

    /** 某配送员手里的单：pending 待运，completed 已完成 */
    @GetMapping("/{postmanId}/tasks")
    public ApiResult<List<PostmanTask>> tasks(@PathVariable("postmanId") Long postmanId,
                                              @RequestParam(value = "type", required = false) String type,
                                              @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requireAdmin(user);
        Long restrict = user.role() == Role.STATION_ADMIN ? user.stationId() : null;
        return ApiResult.ok(adminPostmanService.tasks(postmanId, type, restrict));
    }

    private void requireAdmin(SessionUser user)
    {
        if (user == null)
        {
            throw new ApiException(401, "请先登录");
        }
        if (user.role() == Role.POSTMAN)
        {
            throw new ApiException(403, "该接口仅限管理员访问");
        }
    }
}
