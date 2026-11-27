package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.service.PostmanOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PostmanOrderServiceImpl implements PostmanOrderService
{
    private final DeliveryOrderMapper orderMapper;
    private final OrderRouteMapper routeMapper;

    public PostmanOrderServiceImpl(DeliveryOrderMapper orderMapper, OrderRouteMapper routeMapper)
    {
        this.orderMapper = orderMapper;
        this.routeMapper = routeMapper;
    }

    @Override
    public List<PostmanTask> tasks(Long postmanId, String type)
    {
        if (postmanId == null)
        {
            throw new ApiException(401, "请先登录");
        }
        // 快递员只碰最后一公里，因此按订单状态筛：
        // SENDING = 货已到目的站，等他取件；DELIVERING = 已取件在派送
        List<String> states = "completed".equalsIgnoreCase(type)
                ? List.of("RECEIVED", "REFUSED")
                : List.of("SENDING", "DELIVERING");
        return routeMapper.findTasks(postmanId, states);
    }

    @Override
    @Transactional
    public void pickup(String orderNo, Long postmanId)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (postmanId == null)
        {
            throw new ApiException(400, "快递员不能为空");
        }
        if (order.getState() != OrderState.SENDING)
        {
            throw new ApiException(409, "当前状态不可取件：" + order.getState());
        }
        if (order.getCurrentSeq() == null || order.getForwardStationCount() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }
        if (!order.getCurrentSeq().equals(order.getForwardStationCount()))
        {
            throw new ApiException(409, "货物尚未到达目的站");
        }

        OrderRoute leg = routeMapper.findByOrderIdAndSeq(order.getId(), order.getForwardStationCount());
        if (leg == null)
        {
            throw new ApiException(409, "目的站路段不存在");
        }
        if (!Objects.equals(leg.getPostmanId(), postmanId))
        {
            throw new ApiException(403, "该订单未指派给您");
        }
        if (leg.getStatus() != RouteStatus.ARRIVED)
        {
            throw new ApiException(409, "货物尚未到站，无法取件");
        }

        if (routeMapper.markDepart(order.getId(), order.getForwardStationCount(), LocalDateTime.now()) == 0)
        {
            throw new ApiException(409, "已取件，请勿重复扫码");
        }
        if (routeMapper.updateStatus(order.getId(), order.getForwardStationCount(), RouteStatus.DELIVERING) == 0)
        {
            throw new ApiException(409, "取件失败，路段状态已变化");
        }
        if (orderMapper.transition(orderNo, OrderState.SENDING, OrderState.DELIVERING) == 0)
        {
            throw new ApiException(409, "取件失败，订单状态已变化");
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
}
