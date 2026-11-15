package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.StationView;
import norym.logisticsdeliverysystem.service.StationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开接口：下单页需要选起止站，用户未登录也要能拿到。
 */
@RestController
@RequestMapping("/api/station")
public class StationController
{
    private final StationService stationService;

    public StationController(StationService stationService)
    {
        this.stationService = stationService;
    }

    @GetMapping("/list")
    public ApiResult<List<StationView>> list()
    {
        return ApiResult.ok(stationService.list());
    }
}
