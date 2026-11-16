package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.PostmanActionRequest;
import norym.logisticsdeliverysystem.service.PostmanOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮递员扫码接口。二维码内容就是订单号，动作由路径区分。
 * <p>
 * postmanId 暂时由请求体传入，Session 上线后从会话取。
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

    /** 扫码开始送货 */
    @PostMapping("/{orderNo}/depart")
    public ApiResult<Void> depart(@PathVariable("orderNo") String orderNo,
                                  @RequestBody PostmanActionRequest request)
    {
        postmanOrderService.depart(orderNo, request == null ? null : request.postmanId());
        return ApiResult.ok();
    }

    /** 扫码确认货到下一站 */
    @PostMapping("/{orderNo}/arrive")
    public ApiResult<Void> arrive(@PathVariable("orderNo") String orderNo,
                                  @RequestBody PostmanActionRequest request)
    {
        postmanOrderService.arrive(orderNo, request == null ? null : request.postmanId());
        return ApiResult.ok();
    }
}
