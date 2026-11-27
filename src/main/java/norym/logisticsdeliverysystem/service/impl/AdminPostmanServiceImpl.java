package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.PostmanInfo;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.entity.Postman;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.mapper.PostmanMapper;
import norym.logisticsdeliverysystem.service.AdminPostmanService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class AdminPostmanServiceImpl implements AdminPostmanService
{
    private final PostmanMapper postmanMapper;
    private final OrderRouteMapper routeMapper;

    public AdminPostmanServiceImpl(PostmanMapper postmanMapper, OrderRouteMapper routeMapper)
    {
        this.postmanMapper = postmanMapper;
        this.routeMapper = routeMapper;
    }

    @Override
    public List<PostmanInfo> list(Long stationId)
    {
        return postmanMapper.findAllWithTaskCount(stationId);
    }

    @Override
    public List<PostmanTask> tasks(Long postmanId, String type, Long restrictStationId)
    {
        if (postmanId == null)
        {
            throw new ApiException(400, "配送员 id 不能为空");
        }
        Postman postman = postmanMapper.findById(postmanId);
        if (postman == null)
        {
            throw new ApiException(404, "配送员不存在");
        }
        // 节点管理员只能看本站的人
        if (restrictStationId != null && !Objects.equals(restrictStationId, postman.getStationId()))
        {
            throw new ApiException(403, "只能查看本站配送员的任务");
        }

        // findTasks 按订单状态过滤，与快递员自己的任务列表、卡片上的单量统计一致
        List<String> orderStates = "completed".equalsIgnoreCase(type)
                ? List.of("RECEIVED", "REFUSED")
                : List.of("SENDING", "DELIVERING");
        return routeMapper.findTasks(postmanId, orderStates);
    }
}
