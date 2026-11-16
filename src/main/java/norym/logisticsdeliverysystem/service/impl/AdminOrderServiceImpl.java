package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.ApproveRequest;
import norym.logisticsdeliverysystem.dto.AssignRequest;
import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.RoutePlanView;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.Postman;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import norym.logisticsdeliverysystem.entity.Station;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.mapper.PostmanMapper;
import norym.logisticsdeliverysystem.mapper.StationMapper;
import norym.logisticsdeliverysystem.service.AdminOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminOrderServiceImpl implements AdminOrderService
{
    private final DeliveryOrderMapper orderMapper;
    private final OrderRouteMapper routeMapper;
    private final StationMapper stationMapper;
    private final PostmanMapper postmanMapper;
    private final RoutePlanner routePlanner;

    public AdminOrderServiceImpl(DeliveryOrderMapper orderMapper,
                                 OrderRouteMapper routeMapper,
                                 StationMapper stationMapper,
                                 PostmanMapper postmanMapper,
                                 RoutePlanner routePlanner)
    {
        this.orderMapper = orderMapper;
        this.routeMapper = routeMapper;
        this.stationMapper = stationMapper;
        this.postmanMapper = postmanMapper;
        this.routePlanner = routePlanner;
    }

    @Override
    public List<OrderBriefView> pending()
    {
        Map<Long, Station> stations = loadStations();
        List<OrderBriefView> result = new ArrayList<>();
        for (DeliveryOrder order : orderMapper.findByState(OrderState.WAITING))
        {
            result.add(new OrderBriefView(order.getOrderNo(),
                    order.getState().name(),
                    stationName(stations, order.getFromStationId()),
                    stationName(stations, order.getToStationId()),
                    order.getCreateTime()));
        }
        return result;
    }

    @Override
    public RoutePlanView plan(String orderNo)
    {
        DeliveryOrder order = requireOrder(orderNo);
        return routePlanner.describe(routePlanner.shortestPath(order.getFromStationId(), order.getToStationId()));
    }

    @Override
    @Transactional
    public void approve(String orderNo, ApproveRequest request)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.WAITING)
        {
            throw new ApiException(409, "只有待审批的订单可以批准，当前状态：" + order.getState());
        }

        List<Long> path = request == null ? null : request.stationIds();
        if (path == null || path.isEmpty())
        {
            path = routePlanner.shortestPath(order.getFromStationId(), order.getToStationId());
        }
        else
        {
            routePlanner.validatePath(path, order.getFromStationId(), order.getToStationId());
        }

        int rows = orderMapper.approve(orderNo,
                request == null ? null : request.operatorId(),
                LocalDateTime.now(), path.size(), order.getFromStationId());
        if (rows == 0)
        {
            throw new ApiException(409, "批准失败，订单状态已变化");
        }

        // 先清后写：重复批准时不会留下两份路线
        routeMapper.deleteByOrderId(order.getId());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < path.size(); i++)
        {
            OrderRoute route = new OrderRoute();
            route.setOrderId(order.getId());
            route.setSeq(i + 1);
            route.setStationId(path.get(i));
            // 始发站视为已到站：货本来就在站里，界面不该显示"未到达"
            route.setStatus(i == 0 ? RouteStatus.ARRIVED : RouteStatus.PENDING);
            route.setActualArriveTime(i == 0 ? now : null);
            routeMapper.insert(route);
        }
    }

    @Override
    public void reject(String orderNo, String reason)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.WAITING)
        {
            throw new ApiException(409, "只有待审批的订单可以拒绝，当前状态：" + order.getState());
        }
        String finalReason = reason == null || reason.isBlank() ? "管理员拒绝" : reason.trim();
        if (orderMapper.reject(orderNo, finalReason) == 0)
        {
            throw new ApiException(409, "拒绝失败，订单状态已变化");
        }
    }

    @Override
    @Transactional
    public void assign(String orderNo, AssignRequest request)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.APPROVED && order.getState() != OrderState.SENDING)
        {
            throw new ApiException(409, "当前状态不可指派运力：" + order.getState());
        }
        if (order.getCurrentSeq() == null || order.getForwardStationCount() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }
        int nextSeq = order.getCurrentSeq() + 1;
        if (nextSeq > order.getForwardStationCount())
        {
            throw new ApiException(409, "已到达目的站，无需再指派");
        }
        if (request == null || request.postmanId() == null)
        {
            throw new ApiException(400, "邮递员不能为空");
        }

        Postman postman = postmanMapper.findById(request.postmanId());
        if (postman == null)
        {
            throw new ApiException(400, "邮递员不存在");
        }
        if (postman.getStatus() != null && postman.getStatus() == 0)
        {
            throw new ApiException(400, "该邮递员已离岗");
        }

        // 指派写在"到达站"那一行：表示这一段的承运人是谁
        int rows = routeMapper.assign(order.getId(), nextSeq, postman.getId(), request.tripNo());
        if (rows == 0)
        {
            throw new ApiException(409, "指派失败，该路段已发运或不存在");
        }
    }

    private DeliveryOrder requireOrder(String orderNo)
    {
        if (orderNo == null || orderNo.isBlank())
        {
            throw new ApiException(400, "订单号不能为空");
        }
        DeliveryOrder order = orderMapper.findByOrderNo(orderNo.trim());
        if (order == null)
        {
            throw new ApiException(404, "订单不存在：" + orderNo);
        }
        return order;
    }

    private Map<Long, Station> loadStations()
    {
        Map<Long, Station> stations = new HashMap<>();
        for (Station station : stationMapper.findAll())
        {
            stations.put(station.getId(), station);
        }
        return stations;
    }

    private String stationName(Map<Long, Station> stations, Long id)
    {
        Station station = id == null ? null : stations.get(id);
        return station == null ? null : station.getName();
    }
}
