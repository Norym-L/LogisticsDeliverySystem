package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.service.PostmanOrderService;
import norym.logisticsdeliverysystem.service.PricingService;
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
    private final PricingService pricingService;

    public PostmanOrderServiceImpl(DeliveryOrderMapper orderMapper,
                                   OrderRouteMapper routeMapper,
                                   PricingService pricingService)
    {
        this.orderMapper = orderMapper;
        this.routeMapper = routeMapper;
        this.pricingService = pricingService;
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
    public PriceQuoteView confirmPrice(String orderNo, Long postmanId, ConfirmPriceRequest request)
    {
        DeliveryOrder order = requireAssignedLastMile(orderNo, postmanId, true);
        if (request == null)
        {
            throw new ApiException(400, "请录入体积和质量");
        }
        pricingService.validateMeasures(request.weightKg(), request.volumeCm3());

        LocalDateTime now = LocalDateTime.now();
        PriceQuoteView quote = pricingService.calculate(
                request.weightKg(),
                request.volumeCm3(),
                pricingService.distanceForOrder(order));
        if (orderMapper.confirmPrice(order.getOrderNo(), request.volumeCm3(), request.weightKg(),
                quote.freight(), now, postmanId) == 0)
        {
            throw new ApiException(409, "核价失败，订单状态已变化");
        }
        return quote.withConfirmation(order.getOrderNo(), now);
    }

    @Override
    @Transactional
    public void pickup(String orderNo, Long postmanId, ConfirmPriceRequest request)
    {
        DeliveryOrder order = requireAssignedLastMile(orderNo, postmanId, false);
        if (order.getState() != OrderState.SENDING)
        {
            throw new ApiException(409, "当前状态不可取件：" + order.getState());
        }

        OrderRoute leg = lastMileLeg(order);
        if (leg.getStatus() != RouteStatus.ARRIVED)
        {
            throw new ApiException(409, "货物尚未到站，无法取件");
        }

        if (!order.isPriceConfirmed())
        {
            if (request == null || request.volumeCm3() == null || request.weightKg() == null)
            {
                throw new ApiException(400, "取件前请先录入体积和质量并核价");
            }
            confirmPrice(orderNo, postmanId, request);
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

    /**
     * @param allowDelivering true 时允许派送中再核价；取件只允许货已到目的站待取件
     */
    private DeliveryOrder requireAssignedLastMile(String orderNo, Long postmanId, boolean allowDelivering)
    {
        if (postmanId == null)
        {
            throw new ApiException(400, "快递员不能为空");
        }
        DeliveryOrder order = requireOrder(orderNo);
        boolean sendingLastMile = order.getState() == OrderState.SENDING
                && order.getCurrentSeq() != null
                && order.getForwardStationCount() != null
                && order.getCurrentSeq().equals(order.getForwardStationCount());
        boolean delivering = order.getState() == OrderState.DELIVERING;
        if (!(sendingLastMile || (allowDelivering && delivering)))
        {
            if (order.getState() == OrderState.SENDING)
            {
                throw new ApiException(409, "货物尚未到达目的站");
            }
            throw new ApiException(409, "当前状态不可核价：" + order.getState());
        }
        if (order.getCurrentSeq() == null || order.getForwardStationCount() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }

        OrderRoute leg = lastMileLeg(order);
        if (!Objects.equals(leg.getPostmanId(), postmanId))
        {
            throw new ApiException(403, "该订单未指派给您");
        }
        return order;
    }

    private OrderRoute lastMileLeg(DeliveryOrder order)
    {
        if (order.getForwardStationCount() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }
        OrderRoute leg = routeMapper.findByOrderIdAndSeq(order.getId(), order.getForwardStationCount());
        if (leg == null)
        {
            throw new ApiException(409, "目的站路段不存在");
        }
        return leg;
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
