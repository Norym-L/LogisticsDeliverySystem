package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.auth.AuthService;
import norym.logisticsdeliverysystem.auth.Role;
import norym.logisticsdeliverysystem.auth.SessionUser;
import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.service.PostmanOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

/**
 * 快递员接口：只涉及最后一公里。
 * <p>
 * 中转运输由车次承运，快递员不参与，所以这里没有发运/到站的概念，
 * 只有"取件开始派送"，随后由收件人扫码签收。
 */
@RestController
@RequestMapping("/api/postman/orders")
public class PostmanController
{
    private final PostmanOrderService postmanOrderService;

    public PostmanController(PostmanOrderService postmanOrderService)
    {
        this.postmanOrderService = postmanOrderService;
    }

    /** 我的任务：登录后先看这个，再去取件 */
    @GetMapping
    public ApiResult<List<PostmanTask>> tasks(@RequestParam(value = "type", required = false) String type,
                                              @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requirePostman(user);
        return ApiResult.ok(postmanOrderService.tasks(user.id(), type));
    }

    /** 扫码取件，开始派送 */
    @PostMapping("/{orderNo}/pickup")
    public ApiResult<Void> pickup(@PathVariable("orderNo") String orderNo,
                                  @SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        requirePostman(user);
        postmanOrderService.pickup(orderNo, user.id());
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
