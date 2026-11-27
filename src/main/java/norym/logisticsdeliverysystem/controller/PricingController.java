package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.PriceQuoteRequest;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;
import norym.logisticsdeliverysystem.service.PricingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开运费试算，不写库，不需要登录。
 */
@RestController
@RequestMapping("/api/pricing")
public class PricingController
{
    private final PricingService pricingService;

    public PricingController(PricingService pricingService)
    {
        this.pricingService = pricingService;
    }

    @PostMapping("/quote")
    public ApiResult<PriceQuoteView> quote(@RequestBody PriceQuoteRequest request)
    {
        return ApiResult.ok(pricingService.quote(request));
    }
}
