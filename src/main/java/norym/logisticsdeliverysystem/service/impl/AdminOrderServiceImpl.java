package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.FieldLimits;
import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.RoutePlanView;
import norym.logisticsdeliverysystem.dto.StationInboxItem;
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
import java.util.Objects;

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
                    order.getCreateTime(),
                    order.getApproveDeadline()));
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
    public void approve(String orderNo, Long operatorId, List<Long> stationIds)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.WAITING)
        {
            throw new ApiException(409, "只有待审批的订单可以批准，当前状态：" + order.getState());
        }

        List<Long> path = stationIds;
        if (path == null || path.isEmpty())
        {
            path = routePlanner.shortestPath(order.getFromStationId(), order.getToStationId());
        }
        else
        {
            routePlanner.validatePath(path, order.getFromStationId(), order.getToStationId());
        }

        int rows = orderMapper.approve(orderNo, operatorId,
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
        if (finalReason.length() > FieldLimits.REASON)
        {
            throw new ApiException(400, "拒绝原因不能超过 " + FieldLimits.REASON + " 个字");
        }
        if (orderMapper.reject(orderNo, finalReason) == 0)
        {
            throw new ApiException(409, "拒绝失败，订单状态已变化");
        }
    }

    @Override
    public void assignTrip(String orderNo, Long operatorStationId, String tripNo)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.APPROVED && order.getState() != OrderState.SENDING)
        {
            throw new ApiException(409, "当前状态不可指派车次：" + order.getState());
        }
        requireCurrentStation(order, operatorStationId, "只能为本站货物指派车次");

        int nextSeq = nextSeq(order);
        if (nextSeq > order.getForwardStationCount())
        {
            throw new ApiException(409, "已到达目的站，无需再指派车次");
        }
        if (tripNo == null || tripNo.isBlank())
        {
            throw new ApiException(400, "车次不能为空");
        }
        String trip = tripNo.trim();
        if (trip.length() > FieldLimits.TRIP_NO)
        {
            throw new ApiException(400, "车次不能超过 " + FieldLimits.TRIP_NO + " 个字");
        }

        if (routeMapper.assignTrip(order.getId(), nextSeq, trip) == 0)
        {
            throw new ApiException(409, "指派失败，该路段已到站或不存在");
        }
    }

    @Override
    public void assignCourier(String orderNo, Long operatorStationId, Long postmanId)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.SENDING)
        {
            throw new ApiException(409, "货物需先到达目的站才能指派快递员，当前状态：" + order.getState());
        }
        if (order.getCurrentSeq() == null || order.getForwardStationCount() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }
        if (!order.getCurrentSeq().equals(order.getForwardStationCount()))
        {
            throw new ApiException(409, "货物尚未到达目的站");
        }
        if (operatorStationId != null && !operatorStationId.equals(order.getToStationId()))
        {
            throw new ApiException(403, "只能由目的站指派快递员");
        }
        if (postmanId == null)
        {
            throw new ApiException(400, "快递员不能为空");
        }

        Postman postman = postmanMapper.findById(postmanId);
        if (postman == null)
        {
            throw new ApiException(400, "快递员不存在");
        }
        if (postman.getStatus() != null && postman.getStatus() == 0)
        {
            throw new ApiException(400, "该快递员已离岗");
        }
        if (!Objects.equals(postman.getStationId(), order.getToStationId()))
        {
            Station target = stationMapper.findById(order.getToStationId());
            String targetName = target == null ? String.valueOf(order.getToStationId()) : target.getName();
            throw new ApiException(400, "最后一公里须由目的站（" + targetName + "）的快递员承运");
        }

        if (routeMapper.assignPostman(order.getId(), order.getForwardStationCount(), postman.getId()) == 0)
        {
            throw new ApiException(409, "指派失败，目的站路段不存在");
        }
    }

    @Override
    @Transactional
    public void depart(String orderNo, Long operatorStationId)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.APPROVED && order.getState() != OrderState.SENDING)
        {
            throw new ApiException(409, "当前状态不可发运：" + order.getState());
        }
        requireCurrentStation(order, operatorStationId, "只能从本站发运");

        int nextSeq = nextSeq(order);
        OrderRoute leg = routeMapper.findByOrderIdAndSeq(order.getId(), nextSeq);
        if (leg == null)
        {
            throw new ApiException(409, "没有下一站可发运");
        }
        // 中转段由车次承运，没登记车次就不该发车
        if (leg.getArriveTripNo() == null || leg.getArriveTripNo().isBlank())
        {
            throw new ApiException(400, "请先为下一段指派车次");
        }

        OrderRoute current = routeMapper.findByOrderIdAndSeq(order.getId(), order.getCurrentSeq());
        if (current != null && current.getDepartTime() != null)
        {
            throw new ApiException(409, "该路段已发运，请勿重复操作");
        }
        if (routeMapper.markDepart(order.getId(), order.getCurrentSeq(), LocalDateTime.now()) == 0)
        {
            throw new ApiException(409, "发运失败，该路段已发运");
        }

        if (order.getState() == OrderState.APPROVED
                && orderMapper.transition(orderNo, OrderState.APPROVED, OrderState.SENDING) == 0)
        {
            throw new ApiException(409, "发运失败，订单状态已变化");
        }
    }

    @Override
    @Transactional
    public void arrive(String orderNo, Long operatorStationId)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.SENDING)
        {
            throw new ApiException(409, "当前状态不可确认到货：" + order.getState());
        }
        if (order.getCurrentSeq() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }

        int prevSeq = order.getCurrentSeq();
        int nextSeq = prevSeq + 1;
        OrderRoute leg = routeMapper.findByOrderIdAndSeq(order.getId(), nextSeq);
        if (leg == null)
        {
            throw new ApiException(409, "没有下一站可确认");
        }
        OrderRoute current = routeMapper.findByOrderIdAndSeq(order.getId(), prevSeq);
        if (current == null || current.getDepartTime() == null)
        {
            throw new ApiException(409, "上一站尚未发运，不能确认到货");
        }
        if (leg.getArriveTripNo() == null || leg.getArriveTripNo().isBlank())
        {
            throw new ApiException(400, "下一段尚未登记车次，不能确认到货");
        }
        // 到货由目的站确认：货到了谁家谁签收
        if (operatorStationId != null && !operatorStationId.equals(leg.getStationId()))
        {
            throw new ApiException(403, "只能确认本站的到货");
        }

        if (routeMapper.markArrive(order.getId(), nextSeq, LocalDateTime.now()) == 0)
        {
            throw new ApiException(409, "该路段已确认到货，请勿重复操作");
        }

        // 到货后仍是 SENDING：快递员取件才转 DELIVERING
        if (orderMapper.advance(orderNo, nextSeq, leg.getStationId(), OrderState.SENDING, prevSeq) == 0)
        {
            throw new ApiException(409, "推进失败，订单状态已变化");
        }
    }

    @Override
    public List<StationInboxItem> inbox(Long stationId)
    {
        if (stationId == null)
        {
            throw new ApiException(403, "当前账号未绑定驿站");
        }
        List<StationInboxItem> items = new ArrayList<>();
        items.addAll(orderMapper.findPendingDepart(stationId));
        items.addAll(orderMapper.findPendingArrive(stationId));
        items.addAll(orderMapper.findPendingDispatch(stationId));
        return items;
    }

    private void requireCurrentStation(DeliveryOrder order, Long operatorStationId, String message)
    {
        if (order.getCurrentSeq() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }
        if (operatorStationId != null && !operatorStationId.equals(order.getCurrentStationId()))
        {
            throw new ApiException(403, message);
        }
    }

    private int nextSeq(DeliveryOrder order)
    {
        return order.getCurrentSeq() == null ? 1 : order.getCurrentSeq() + 1;
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
