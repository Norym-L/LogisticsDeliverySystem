package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.service.PostmanOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    @Transactional
    public void depart(String orderNo, Long postmanId)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (postmanId == null)
        {
            throw new ApiException(400, "邮递员不能为空");
        }
        if (order.getState() != OrderState.APPROVED
                && order.getState() != OrderState.SENDING
                && order.getState() != OrderState.RETURNING)
        {
            throw new ApiException(409, "当前状态不可发运：" + order.getState());
        }
        if (order.getCurrentSeq() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }

        int nextSeq = order.getCurrentSeq() + 1;
        OrderRoute leg = routeMapper.findByOrderIdAndSeq(order.getId(), nextSeq);
        if (leg == null)
        {
            throw new ApiException(409, "没有下一站可发运");
        }
        if (!Objects.equals(leg.getPostmanId(), postmanId))
        {
            throw new ApiException(403, "该路段未指派给您");
        }

        // 重复发运的幂等判断要看"出发站"那一行，而不是下一站
        OrderRoute departFrom = routeMapper.findByOrderIdAndSeq(order.getId(), order.getCurrentSeq());
        if (departFrom != null && departFrom.getDepartTime() != null)
        {
            throw new ApiException(409, "该路段已发运，请勿重复扫码");
        }

        // 出发时间记在"当前站"那一行，表示货从本站发出
        routeMapper.markDepart(order.getId(), order.getCurrentSeq(), LocalDateTime.now());
        if (order.getState() == OrderState.APPROVED)
        {
            orderMapper.updateState(orderNo, OrderState.SENDING);
        }
    }

    @Override
    @Transactional
    public void arrive(String orderNo, Long postmanId)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (postmanId == null)
        {
            throw new ApiException(400, "邮递员不能为空");
        }
        if (order.getState() != OrderState.SENDING && order.getState() != OrderState.RETURNING)
        {
            throw new ApiException(409, "当前状态不可确认到站：" + order.getState());
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
        if (!Objects.equals(leg.getPostmanId(), postmanId))
        {
            throw new ApiException(403, "该路段未指派给您");
        }

        if (routeMapper.markArrive(order.getId(), nextSeq, LocalDateTime.now()) == 0)
        {
            throw new ApiException(409, "该路段已确认到站，请勿重复扫码");
        }

        OrderState newState = order.getState();
        if (order.getState() == OrderState.SENDING
                && Objects.equals(nextSeq, order.getForwardStationCount()))
        {
            // 到达目的站，等待收件人扫码签收
            newState = OrderState.DELIVERING;
        }
        else if (order.getState() == OrderState.RETURNING)
        {
            int totalLegs = routeMapper.findByOrderId(order.getId()).size();
            if (nextSeq >= totalLegs)
            {
                newState = OrderState.RETURNED;
            }
        }

        if (orderMapper.advance(orderNo, nextSeq, leg.getStationId(), newState, prevSeq) == 0)
        {
            throw new ApiException(409, "推进失败，订单状态已变化");
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
