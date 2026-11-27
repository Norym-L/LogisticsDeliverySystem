package norym.logisticsdeliverysystem.support;

import norym.logisticsdeliverysystem.dto.PostmanInfo;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.Postman;
import norym.logisticsdeliverysystem.entity.RouteEdge;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import norym.logisticsdeliverysystem.entity.Station;
import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.mapper.PostmanMapper;
import norym.logisticsdeliverysystem.mapper.RouteEdgeMapper;
import norym.logisticsdeliverysystem.mapper.StationMapper;
import norym.logisticsdeliverysystem.service.AdminOrderService;
import norym.logisticsdeliverysystem.service.AdminPostmanService;
import norym.logisticsdeliverysystem.service.OrderService;
import norym.logisticsdeliverysystem.service.PostmanOrderService;
import norym.logisticsdeliverysystem.service.impl.AdminOrderServiceImpl;
import norym.logisticsdeliverysystem.service.impl.AdminPostmanServiceImpl;
import norym.logisticsdeliverysystem.service.impl.OrderServiceImpl;
import norym.logisticsdeliverysystem.service.impl.PostmanOrderServiceImpl;
import norym.logisticsdeliverysystem.service.impl.RoutePlanner;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务层测试用的内存库。语义对齐 MyBatis XML 里的条件更新，不连接 MySQL。
 */
public final class FlowWorld
{
    private final AtomicLong ids = new AtomicLong(1);
    private final List<Station> stations = new ArrayList<>();
    private final List<RouteEdge> edges = new ArrayList<>();
    private final List<Postman> postmen = new ArrayList<>();
    private final List<DeliveryOrder> orders = new ArrayList<>();
    private final List<OrderRoute> routes = new ArrayList<>();

    private final DeliveryOrderMapper orderMapper = new Orders();
    private final OrderRouteMapper routeMapper = new Routes();
    private final StationMapper stationMapper = new Stations();
    private final PostmanMapper postmanMapper = new Postmen();
    private final RouteEdgeMapper edgeMapper = new Edges();

    public final OrderService orderService;
    public final AdminOrderService adminOrders;
    public final PostmanOrderService postmanOrders;
    public final AdminPostmanService adminPostmen;

    private FlowWorld()
    {
        RoutePlanner planner = new RoutePlanner(edgeMapper, stationMapper);
        this.orderService = new OrderServiceImpl(orderMapper, routeMapper, stationMapper, 600, 3);
        this.adminOrders = new AdminOrderServiceImpl(orderMapper, routeMapper, stationMapper, postmanMapper, planner);
        this.postmanOrders = new PostmanOrderServiceImpl(orderMapper, routeMapper);
        this.adminPostmen = new AdminPostmanServiceImpl(postmanMapper, routeMapper);
    }

    public static FlowWorld seed()
    {
        FlowWorld world = new FlowWorld();
        world.station(1, "ST001", "北京转运中心");
        world.station(2, "ST002", "天津转运中心");
        world.station(3, "ST003", "济南转运中心");
        world.station(4, "ST004", "郑州转运中心");
        world.station(5, "ST005", "南京转运中心");
        world.station(6, "ST006", "上海转运中心");
        world.station(7, "ST007", "武汉转运中心");
        world.station(8, "ST008", "杭州转运中心");
        world.edge(1, 2, "137");
        world.edge(1, 4, "690");
        world.edge(2, 3, "320");
        world.edge(3, 4, "440");
        world.edge(3, 5, "620");
        world.edge(4, 7, "520");
        world.edge(5, 6, "300");
        world.edge(5, 7, "540");
        world.edge(5, 8, "280");
        world.edge(6, 8, "175");

        Postman courier = new Postman();
        courier.setId(11L);
        courier.setUsername("pm011");
        courier.setName("吴申城");
        courier.setStationId(6L);
        courier.setStatus(1);
        world.postmen.add(courier);
        return world;
    }

    public void clearTrip(String orderNo, int seq)
    {
        route(orderNo, seq).setArriveTripNo(null);
    }

    public void setTrip(String orderNo, int seq, String tripNo)
    {
        route(orderNo, seq).setArriveTripNo(tripNo);
    }

    public RouteStatus routeStatus(String orderNo, int seq)
    {
        return route(orderNo, seq).getStatus();
    }

    public OrderState orderState(String orderNo)
    {
        return orderMapper.findByOrderNo(orderNo).getState();
    }

    public void markReturnOf(String orderNo, String originalNo)
    {
        orderMapper.findByOrderNo(orderNo).setReturnOf(originalNo);
    }

    private OrderRoute route(String orderNo, int seq)
    {
        DeliveryOrder order = orderMapper.findByOrderNo(orderNo);
        OrderRoute leg = routeMapper.findByOrderIdAndSeq(order.getId(), seq);
        if (leg == null)
        {
            throw new IllegalStateException("路段不存在 seq=" + seq);
        }
        return leg;
    }

    private void station(long id, String code, String name)
    {
        Station station = new Station();
        station.setId(id);
        station.setCode(code);
        station.setName(name);
        station.setStatus(1);
        stations.add(station);
    }

    private void edge(long a, long b, String km)
    {
        RouteEdge edge = new RouteEdge();
        edge.setId(ids.getAndIncrement());
        edge.setStationAId(Math.min(a, b));
        edge.setStationBId(Math.max(a, b));
        edge.setDistanceKm(new BigDecimal(km));
        edge.setStatus(1);
        edges.add(edge);
    }

    private final class Orders implements DeliveryOrderMapper
    {
        @Override
        public int insert(DeliveryOrder order)
        {
            if (order.getId() == null)
            {
                order.setId(ids.getAndIncrement());
            }
            if (order.getResubmitCount() == null)
            {
                order.setResubmitCount(0);
            }
            if (order.getCreateTime() == null)
            {
                order.setCreateTime(LocalDateTime.now());
            }
            orders.add(order);
            return 1;
        }

        @Override
        public DeliveryOrder findByOrderNo(String orderNo)
        {
            return orders.stream().filter(o -> orderNo.equals(o.getOrderNo())).findFirst().orElse(null);
        }

        @Override
        public DeliveryOrder findByReturnOf(String orderNo)
        {
            return orders.stream()
                    .filter(o -> orderNo.equals(o.getReturnOf()))
                    .max(Comparator.comparing(DeliveryOrder::getId))
                    .orElse(null);
        }

        @Override
        public List<DeliveryOrder> findByPhone(String phone)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int sign(String orderNo, String receiverPhone, LocalDateTime now)
        {
            DeliveryOrder order = findByOrderNo(orderNo);
            if (order == null || order.getState() != OrderState.DELIVERING
                    || !receiverPhone.equals(order.getReceiverPhone()))
            {
                return 0;
            }
            order.setState(OrderState.RECEIVED);
            order.setReceiveTime(now);
            return 1;
        }

        @Override
        public int refuse(String orderNo, String receiverPhone, String reason, LocalDateTime now)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int resubmit(DeliveryOrder update, LocalDateTime deadline, int maxResubmit)
        {
            DeliveryOrder order = findByOrderNo(update.getOrderNo());
            int used = order.getResubmitCount() == null ? 0 : order.getResubmitCount();
            if (order.getState() != OrderState.REJECTED || used >= maxResubmit)
            {
                return 0;
            }
            order.setSenderName(update.getSenderName());
            order.setSenderPhone(update.getSenderPhone());
            order.setReceiverName(update.getReceiverName());
            order.setReceiverPhone(update.getReceiverPhone());
            order.setFromStationId(update.getFromStationId());
            order.setToStationId(update.getToStationId());
            order.setGoodsDesc(update.getGoodsDesc());
            order.setWeight(update.getWeight());
            order.setState(OrderState.WAITING);
            order.setRejectReason(null);
            order.setCurrentSeq(null);
            order.setCurrentStationId(null);
            order.setApproveDeadline(deadline);
            order.setResubmitCount(used + 1);
            return 1;
        }

        @Override
        public int rejectTimeout(LocalDateTime now)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DeliveryOrder> findByState(OrderState state)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<norym.logisticsdeliverysystem.dto.StationInboxItem> findPendingDepart(Long stationId)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<norym.logisticsdeliverysystem.dto.StationInboxItem> findPendingArrive(Long stationId)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<norym.logisticsdeliverysystem.dto.StationInboxItem> findPendingDispatch(Long stationId)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int approve(String orderNo, Long operatorId, LocalDateTime now, int stationCount, Long fromStationId)
        {
            DeliveryOrder order = findByOrderNo(orderNo);
            if (order == null || order.getState() != OrderState.WAITING)
            {
                return 0;
            }
            order.setState(OrderState.APPROVED);
            order.setApproveBy(operatorId);
            order.setApproveTime(now);
            order.setForwardStationCount(stationCount);
            order.setCurrentSeq(1);
            order.setCurrentStationId(fromStationId);
            return 1;
        }

        @Override
        public int reject(String orderNo, String reason)
        {
            DeliveryOrder order = findByOrderNo(orderNo);
            if (order == null || order.getState() != OrderState.WAITING)
            {
                return 0;
            }
            order.setState(OrderState.REJECTED);
            order.setRejectReason(reason);
            return 1;
        }

        @Override
        public int advance(String orderNo, int seq, Long stationId, OrderState state, Integer prevSeq)
        {
            DeliveryOrder order = findByOrderNo(orderNo);
            if (order == null || !java.util.Objects.equals(order.getCurrentSeq(), prevSeq))
            {
                return 0;
            }
            order.setCurrentSeq(seq);
            order.setCurrentStationId(stationId);
            order.setState(state);
            return 1;
        }

        @Override
        public int transition(String orderNo, OrderState from, OrderState to)
        {
            DeliveryOrder order = findByOrderNo(orderNo);
            if (order == null || order.getState() != from)
            {
                return 0;
            }
            order.setState(to);
            return 1;
        }
    }

    private final class Routes implements OrderRouteMapper
    {
        @Override
        public List<OrderRoute> findByOrderId(Long orderId)
        {
            return routes.stream()
                    .filter(r -> orderId.equals(r.getOrderId()))
                    .sorted(Comparator.comparing(OrderRoute::getSeq))
                    .toList();
        }

        @Override
        public int insert(OrderRoute route)
        {
            route.setId(ids.getAndIncrement());
            routes.add(route);
            return 1;
        }

        @Override
        public int updateStatus(Long orderId, Integer seq, RouteStatus status)
        {
            OrderRoute route = findByOrderIdAndSeq(orderId, seq);
            if (route == null)
            {
                return 0;
            }
            route.setStatus(status);
            return 1;
        }

        @Override
        public OrderRoute findByOrderIdAndSeq(Long orderId, Integer seq)
        {
            return routes.stream()
                    .filter(r -> orderId.equals(r.getOrderId()) && seq.equals(r.getSeq()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int assignTrip(Long orderId, Integer seq, String tripNo)
        {
            OrderRoute route = findByOrderIdAndSeq(orderId, seq);
            if (route == null || route.getStatus() != RouteStatus.PENDING)
            {
                return 0;
            }
            route.setArriveTripNo(tripNo);
            return 1;
        }

        @Override
        public int assignPostman(Long orderId, Integer seq, Long postmanId)
        {
            OrderRoute route = findByOrderIdAndSeq(orderId, seq);
            if (route == null)
            {
                return 0;
            }
            route.setPostmanId(postmanId);
            return 1;
        }

        @Override
        public int markDepart(Long orderId, Integer seq, LocalDateTime now)
        {
            OrderRoute route = findByOrderIdAndSeq(orderId, seq);
            if (route == null || route.getDepartTime() != null)
            {
                return 0;
            }
            route.setDepartTime(now);
            return 1;
        }

        @Override
        public int markArrive(Long orderId, Integer seq, LocalDateTime now)
        {
            OrderRoute route = findByOrderIdAndSeq(orderId, seq);
            if (route == null || route.getStatus() != RouteStatus.PENDING)
            {
                return 0;
            }
            route.setStatus(RouteStatus.ARRIVED);
            route.setActualArriveTime(now);
            return 1;
        }

        @Override
        public int deleteByOrderId(Long orderId)
        {
            routes.removeIf(r -> orderId.equals(r.getOrderId()));
            return 1;
        }

        @Override
        public List<PostmanTask> findTasks(Long postmanId, List<String> orderStates)
        {
            List<PostmanTask> tasks = new ArrayList<>();
            for (OrderRoute route : routes)
            {
                if (!postmanId.equals(route.getPostmanId()))
                {
                    continue;
                }
                DeliveryOrder order = orders.stream()
                        .filter(o -> o.getId().equals(route.getOrderId()))
                        .findFirst()
                        .orElse(null);
                if (order == null || order.getState() == null || !orderStates.contains(order.getState().name()))
                {
                    continue;
                }
                PostmanTask task = new PostmanTask();
                task.setOrderNo(order.getOrderNo());
                task.setOrderState(order.getState().name());
                task.setSeq(route.getSeq());
                task.setForwardStationCount(order.getForwardStationCount());
                task.setLegStatus(route.getStatus() == null ? null : route.getStatus().name());
                tasks.add(task);
            }
            return tasks;
        }
    }

    private final class Stations implements StationMapper
    {
        @Override
        public List<Station> findAll()
        {
            return List.copyOf(stations);
        }

        @Override
        public Station findById(Long id)
        {
            return stations.stream().filter(s -> id.equals(s.getId())).findFirst().orElse(null);
        }
    }

    private final class Postmen implements PostmanMapper
    {
        @Override
        public Postman findById(Long id)
        {
            return postmen.stream().filter(p -> id.equals(p.getId())).findFirst().orElse(null);
        }

        @Override
        public Postman findByUsername(String username)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PostmanInfo> findAllWithTaskCount(Long stationId)
        {
            throw new UnsupportedOperationException();
        }
    }

    private final class Edges implements RouteEdgeMapper
    {
        @Override
        public List<RouteEdge> findAll()
        {
            return edges.stream().filter(e -> e.getStatus() != null && e.getStatus() == 1).toList();
        }
    }
}
