package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.RoutePlanView;
import norym.logisticsdeliverysystem.dto.StationView;
import norym.logisticsdeliverysystem.entity.RouteEdge;
import norym.logisticsdeliverysystem.entity.Station;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.RouteEdgeMapper;
import norym.logisticsdeliverysystem.mapper.StationMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 路线规划：驿站网络是一个带权无向图，用 Dijkstra 求最短路径。
 * <p>
 * 线路只有十几条，每次全量读进内存即可，不需要缓存，也不需要图数据库。
 */
@Component
public class RoutePlanner
{
    private final RouteEdgeMapper edgeMapper;
    private final StationMapper stationMapper;

    public RoutePlanner(RouteEdgeMapper edgeMapper, StationMapper stationMapper)
    {
        this.edgeMapper = edgeMapper;
        this.stationMapper = stationMapper;
    }

    /** 最短路径，返回按顺序排列的驿站 id，首尾分别是起点和终点 */
    public List<Long> shortestPath(Long fromId, Long toId)
    {
        if (fromId == null || toId == null)
        {
            throw new ApiException(400, "起止站不能为空");
        }
        if (fromId.equals(toId))
        {
            return List.of(fromId);
        }

        Map<Long, Map<Long, BigDecimal>> graph = buildGraph();
        Map<Long, Double> distance = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        Set<Long> settled = new HashSet<>();

        distance.put(fromId, 0.0);
        PriorityQueue<Node> queue = new PriorityQueue<>();
        queue.add(new Node(fromId, 0.0));

        while (!queue.isEmpty())
        {
            Node current = queue.poll();
            if (!settled.add(current.id()))
            {
                continue;
            }
            if (current.id().equals(toId))
            {
                break;
            }
            for (Map.Entry<Long, BigDecimal> edge : graph.getOrDefault(current.id(), Map.of()).entrySet())
            {
                Long next = edge.getKey();
                double candidate = current.distance() + edge.getValue().doubleValue();
                if (candidate < distance.getOrDefault(next, Double.MAX_VALUE))
                {
                    distance.put(next, candidate);
                    previous.put(next, current.id());
                    queue.add(new Node(next, candidate));
                }
            }
        }

        if (!distance.containsKey(toId))
        {
            throw new ApiException(400, "两地之间不存在可行路线");
        }

        List<Long> path = new ArrayList<>();
        Long cursor = toId;
        while (cursor != null)
        {
            path.add(cursor);
            cursor = previous.get(cursor);
        }
        Collections.reverse(path);
        return path;
    }

    /** 路段里程合计 */
    public BigDecimal distanceOf(List<Long> path)
    {
        Map<Long, Map<Long, BigDecimal>> graph = buildGraph();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < path.size() - 1; i++)
        {
            BigDecimal km = graph.getOrDefault(path.get(i), Map.of()).get(path.get(i + 1));
            if (km == null)
            {
                throw new ApiException(400, "路线不连通：缺少 " + path.get(i) + " 到 " + path.get(i + 1) + " 的线路");
            }
            total = total.add(km);
        }
        return total;
    }

    /**
     * 校验管理员手动指定的途经节点：
     * 必须包含始发站与目的站、不能重复、相邻两站之间必须有线路。
     */
    public void validatePath(List<Long> path, Long fromId, Long toId)
    {
        if (path == null || path.size() < 2)
        {
            throw new ApiException(400, "途经节点至少包含始发站和目的站");
        }
        if (!fromId.equals(path.get(0)))
        {
            throw new ApiException(400, "途经节点的第一站必须是始发站");
        }
        if (!toId.equals(path.get(path.size() - 1)))
        {
            throw new ApiException(400, "途经节点的最后一站必须是目的站");
        }
        if (new HashSet<>(path).size() != path.size())
        {
            throw new ApiException(400, "途经节点不能重复");
        }
        distanceOf(path);
    }

    public RoutePlanView describe(List<Long> path)
    {
        Map<Long, Station> stations = new HashMap<>();
        for (Station station : stationMapper.findAll())
        {
            stations.put(station.getId(), station);
        }
        List<StationView> views = new ArrayList<>();
        for (Long id : path)
        {
            Station station = stations.get(id);
            if (station == null)
            {
                throw new ApiException(400, "驿站不存在：" + id);
            }
            views.add(new StationView(station.getId(), station.getCode(), station.getName()));
        }
        return new RoutePlanView(views, distanceOf(path));
    }

    private Map<Long, Map<Long, BigDecimal>> buildGraph()
    {
        Map<Long, Map<Long, BigDecimal>> graph = new HashMap<>();
        for (RouteEdge edge : edgeMapper.findAll())
        {
            graph.computeIfAbsent(edge.getStationAId(), k -> new HashMap<>())
                    .put(edge.getStationBId(), edge.getDistanceKm());
            graph.computeIfAbsent(edge.getStationBId(), k -> new HashMap<>())
                    .put(edge.getStationAId(), edge.getDistanceKm());
        }
        return graph;
    }

    private record Node(Long id, double distance) implements Comparable<Node>
    {
        @Override
        public int compareTo(Node other)
        {
            return Double.compare(distance, other.distance);
        }
    }
}
