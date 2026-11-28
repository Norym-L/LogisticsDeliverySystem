package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.TrackPositionView;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import norym.logisticsdeliverysystem.entity.Station;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 根据订单状态和 order_route 的真实进度，计算包裹在地图上的当前位置。
 * <p>
 * 不接车辆实时 GPS：在途时只把标记放在相邻两站经纬度的中点，表示"已从 A 发运、
 * 尚未到达 B"，而不是车辆的真实位置。
 * <p>
 * 状态与位置的对应关系：
 * <ul>
 *   <li>WAITING / REJECTED / CANCELLED：尚未发运，标在始发站；</li>
 *   <li>AWAITING_RECEIPT / APPROVED：货在始发站；</li>
 *   <li>SENDING：当前站已发运且下一站未到 → 两站中点；否则在当前站；</li>
 *   <li>DELIVERING：目的站，最后一公里派送；</li>
 *   <li>RECEIVED / REFUSED：最后已知站（正常为目的地），不再显示为移动中。</li>
 * </ul>
 */
public final class TrackPositionResolver
{
    public static final String MODE_NOT_SHIPPED = "NOT_SHIPPED";
    public static final String MODE_AT_STATION = "AT_STATION";
    public static final String MODE_IN_TRANSIT = "IN_TRANSIT";
    public static final String MODE_DELIVERING = "DELIVERING";
    public static final String MODE_RECEIVED = "RECEIVED";
    public static final String MODE_REFUSED = "REFUSED";

    private TrackPositionResolver()
    {
    }

    public static TrackPositionView resolve(DeliveryOrder order, List<OrderRoute> routes,
                                            Function<Long, Station> stationLookup)
    {
        List<OrderRoute> path = routes == null ? List.of() : routes.stream()
                .filter(route -> route.getSeq() != null)
                .sorted(Comparator.comparing(OrderRoute::getSeq))
                .toList();

        OrderState state = order.getState();
        if (state == null)
        {
            return notShipped(order, path, stationLookup, "尚未发运");
        }

        return switch (state)
        {
            case WAITING -> notShipped(order, path, stationLookup, "尚未发运，等待审批");
            case REJECTED -> notShipped(order, path, stationLookup, "尚未发运（订单已拒绝）");
            case CANCELLED -> notShipped(order, path, stationLookup, "尚未发运（订单已取消）");
            case AWAITING_RECEIPT -> atOrigin(order, path, stationLookup, "在始发站，等待确认收件");
            case APPROVED -> atOrigin(order, path, stationLookup, "在始发站，等待发运");
            case SENDING -> sending(order, path, stationLookup);
            case DELIVERING -> finalPosition(order, path, stationLookup,
                    MODE_DELIVERING, "派送中（最后一公里）");
            case RECEIVED -> finalPosition(order, path, stationLookup,
                    MODE_RECEIVED, "已签收");
            case REFUSED -> finalPosition(order, path, stationLookup,
                    MODE_REFUSED, "已拒收");
        };
    }

    /** 尚未进入运输网：有已固化的路线就取首站，否则回退到订单始发站。 */
    private static TrackPositionView notShipped(DeliveryOrder order, List<OrderRoute> path,
                                                Function<Long, Station> stationLookup, String label)
    {
        OrderRoute first = path.isEmpty() ? null : path.get(0);
        Place place = first == null
                ? place(order.getFromStationId(), 0, stationLookup)
                : place(first, stationLookup);
        return atPlace(MODE_NOT_SHIPPED, label, place);
    }

    /** 已批准但未首次发运：货在始发站，不显示为在途。 */
    private static TrackPositionView atOrigin(DeliveryOrder order, List<OrderRoute> path,
                                              Function<Long, Station> stationLookup, String label)
    {
        OrderRoute first = path.isEmpty() ? null : path.get(0);
        Place place = first == null
                ? place(order.getFromStationId(), 0, stationLookup)
                : place(first, stationLookup);
        return atPlace(MODE_AT_STATION, withStation(label, place), place);
    }

    /** 运送中：current_seq 已发运且下一站未到 → 两站中点；否则在当前站。 */
    private static TrackPositionView sending(DeliveryOrder order, List<OrderRoute> path,
                                             Function<Long, Station> stationLookup)
    {
        if (path.isEmpty())
        {
            Place fallback = place(order.getCurrentStationId() != null
                    ? order.getCurrentStationId() : order.getFromStationId(), 0, stationLookup);
            return atPlace(MODE_AT_STATION, withStation("运送中，位置以站点记录为准", fallback), fallback);
        }

        OrderRoute current = findNode(path, order.getCurrentSeq());
        if (current == null)
        {
            current = lastReached(path);
        }
        OrderRoute next = nextNode(path, current.getSeq());
        if (current.getDepartTime() != null && next != null && next.getActualArriveTime() == null)
        {
            Place from = place(current, stationLookup);
            Place to = place(next, stationLookup);
            BigDecimal latitude = midpoint(from.latitude(), to.latitude());
            BigDecimal longitude = midpoint(from.longitude(), to.longitude());
            String label = "运输中：" + nameOr(from, "上一站") + " → " + nameOr(to, "下一站");
            return new TrackPositionView(MODE_IN_TRANSIT, label, current.getSeq(),
                    from.id(), from.name(), latitude, longitude,
                    from.id(), from.name(), to.id(), to.name());
        }

        Place at = place(current, stationLookup);
        String label = next == null
                ? withStation("已到目的站，等待取件派送", at)
                : withStation("在站等待发运", at);
        return atPlace(MODE_AT_STATION, label, at);
    }

    /** 派送/签收/拒收都停在最后已知站；正常路线下最后一站就是目的站。 */
    private static TrackPositionView finalPosition(DeliveryOrder order, List<OrderRoute> path,
                                                   Function<Long, Station> stationLookup,
                                                   String mode, String label)
    {
        OrderRoute last = path.isEmpty() ? null : path.get(path.size() - 1);
        Place place = last == null
                ? place(order.getToStationId(), 0, stationLookup)
                : place(last, stationLookup);
        return atPlace(mode, withStation(label, place), place);
    }

    private static TrackPositionView atPlace(String mode, String label, Place place)
    {
        if (place == null)
        {
            return new TrackPositionView(mode, label, 0, null, null, null, null,
                    null, null, null, null);
        }
        return new TrackPositionView(mode, label, place.seq(), place.id(), place.name(),
                place.latitude(), place.longitude(), null, null, null, null);
    }

    private static OrderRoute findNode(List<OrderRoute> path, Integer seq)
    {
        if (seq == null)
        {
            return null;
        }
        return path.stream().filter(route -> seq.equals(route.getSeq())).findFirst().orElse(null);
    }

    private static OrderRoute nextNode(List<OrderRoute> path, Integer seq)
    {
        if (seq == null)
        {
            return null;
        }
        return path.stream().filter(route -> Integer.valueOf(seq + 1).equals(route.getSeq()))
                .findFirst().orElse(null);
    }

    /**
     * current_seq 缺失时的兜底：取最后一个留有到站/发运痕迹的节点；
     * 完全没有痕迹就取首站，至少不会把位置画到路线之外。
     */
    private static OrderRoute lastReached(List<OrderRoute> path)
    {
        OrderRoute reached = null;
        for (OrderRoute route : path)
        {
            if (route.getActualArriveTime() != null || route.getDepartTime() != null
                    || route.getStatus() == RouteStatus.ARRIVED
                    || route.getStatus() == RouteStatus.DELIVERING
                    || route.getStatus() == RouteStatus.SIGNED
                    || route.getStatus() == RouteStatus.REFUSED)
            {
                reached = route;
            }
        }
        return reached == null ? path.get(0) : reached;
    }

    private static Place place(OrderRoute route, Function<Long, Station> stationLookup)
    {
        return place(route.getStationId(), route.getSeq() == null ? 0 : route.getSeq(), stationLookup);
    }

    private static Place place(Long stationId, int seq, Function<Long, Station> stationLookup)
    {
        Station station = stationId == null ? null : stationLookup.apply(stationId);
        return new Place(stationId,
                station == null ? null : station.getName(),
                station == null ? null : station.getLatitude(),
                station == null ? null : station.getLongitude(),
                seq);
    }

    private static String withStation(String label, Place place)
    {
        if (place == null || place.name() == null)
        {
            return label;
        }
        // “派送中（最后一公里）”这类已经带括号说明的标签，用间隔号接站名，避免叠括号
        return label.endsWith("）") || label.endsWith(")")
                ? label + " · " + place.name()
                : label + "（" + place.name() + "）";
    }

    private static String nameOr(Place place, String fallback)
    {
        return place == null || place.name() == null ? fallback : place.name();
    }

    private static BigDecimal midpoint(BigDecimal first, BigDecimal second)
    {
        if (first == null)
        {
            return second;
        }
        if (second == null)
        {
            return first;
        }
        return first.add(second).divide(BigDecimal.valueOf(2), 7, RoundingMode.HALF_UP);
    }

    private record Place(Long id, String name, BigDecimal latitude, BigDecimal longitude, int seq)
    {
    }
}
