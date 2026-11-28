package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.DeliverRequest;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    public List<PostmanTask> tasks(Long postmanId, Long stationId, String type)
    {
        if (postmanId == null)
        {
            throw new ApiException(401, "请先登录");
        }
        // 快递员只碰最后一公里，因此按订单状态筛：
        // SENDING = 货已到目的站，等他取件；DELIVERING = 已取件在派送
        boolean completed = "completed".equalsIgnoreCase(type);
        List<String> states = completed
                ? List.of("RECEIVED", "REFUSED")
                : List.of("SENDING", "DELIVERING");
        List<PostmanTask> result = new ArrayList<>();
        if (!completed && stationId != null)
        {
            // 始发站待收件：本站快递员也可以确认收件并核价
            result.addAll(routeMapper.findOriginReceiptTasks(stationId));
        }
        result.addAll(routeMapper.findTasks(postmanId, states));
        return result;
    }

    @Override
    public PostmanTask taskByOrderNo(String orderNo, Long postmanId, Long stationId)
    {
        if (postmanId == null)
        {
            throw new ApiException(401, "请先登录");
        }
        DeliveryOrder order = requireOrder(orderNo);
        PostmanTask deliveryTask = routeMapper.findTaskForPostman(order.getOrderNo(), postmanId);
        if (deliveryTask != null)
        {
            if ("SENDING".equals(deliveryTask.getOrderState())
                    || "DELIVERING".equals(deliveryTask.getOrderState()))
            {
                return deliveryTask;
            }
            throw new ApiException(409, "该订单已完成，不在待办任务中");
        }

        PostmanTask receiptTask = routeMapper.findOriginReceiptTask(order.getOrderNo(), stationId);
        if (receiptTask != null)
        {
            return receiptTask;
        }

        // 查不到任务时给出能区分原因的错误：越权和已结束不能都报 404
        if (order.getState() == OrderState.AWAITING_RECEIPT)
        {
            throw new ApiException(403, "该订单不属于本站的始发收件任务");
        }
        if (order.getState() == OrderState.SENDING || order.getState() == OrderState.DELIVERING)
        {
            OrderRoute leg = order.getForwardStationCount() == null ? null
                    : routeMapper.findByOrderIdAndSeq(order.getId(), order.getForwardStationCount());
            if (leg != null && leg.getPostmanId() != null && !Objects.equals(leg.getPostmanId(), postmanId))
            {
                throw new ApiException(403, "该订单未指派给您");
            }
            throw new ApiException(409, "该订单不在您的待办任务中");
        }
        throw new ApiException(409, "该订单当前不可操作：" + order.getState());
    }

    @Override
    @Transactional
    public PriceQuoteView confirmPrice(String orderNo, Long postmanId, ConfirmPriceRequest request)
    {
        DeliveryOrder order = requireAssignedLastMile(orderNo, postmanId, true);
        // 最终核价已前移到始发站收件。目的站只做只读复核，不再接受改价，
        // 避免出现两个互相矛盾的“最终价”。
        if (!order.isPriceConfirmed())
        {
            throw new ApiException(409, "最终运费尚未在始发站确认，目的站不能核价");
        }
        BigDecimal weight = order.getActualWeight() == null ? order.getWeight() : order.getActualWeight();
        BigDecimal volume = order.getActualVolumeCm3() == null ? order.getVolumeCm3() : order.getActualVolumeCm3();
        PriceQuoteView quote = pricingService.calculate(weight, volume,
                        pricingService.distanceForOrder(order))
                .withEstimatedDays(pricingService.estimateDaysForOrder(order));
        return quote.withConfirmation(order.getOrderNo(), order.getPriceConfirmedAt());
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

        // 运费已在始发站收件时锁定；目的站取件时不再重复改价
        if (!order.isPriceConfirmed())
        {
            throw new ApiException(409, "运费尚未在始发站确认，不能取件");
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

    @Override
    @Transactional
    public void deliver(String orderNo, Long postmanId, DeliverRequest request)
    {
        if (request == null || request.outcome() == null || request.outcome().isBlank())
        {
            throw new ApiException(400, "请选择妥投或派送失败");
        }
        DeliveryOrder order = requireOrder(orderNo);
        if (postmanId == null)
        {
            throw new ApiException(401, "请先登录");
        }
        if (order.getState() != OrderState.DELIVERING)
        {
            if (order.getState() == OrderState.RECEIVED || order.getState() == OrderState.REFUSED)
            {
                throw new ApiException(409, "订单已完成，请勿重复操作");
            }
            throw new ApiException(409, "当前状态不可妥投：" + order.getState());
        }
        if (order.getForwardStationCount() == null)
        {
            throw new ApiException(409, "订单尚未生成路线");
        }
        OrderRoute leg = lastMileLeg(order);
        if (!Objects.equals(leg.getPostmanId(), postmanId))
        {
            throw new ApiException(403, "该订单未指派给您");
        }
        if (leg.getStatus() != RouteStatus.DELIVERING)
        {
            throw new ApiException(409, "该订单不在派送中，请勿重复操作");
        }

        String outcome = request.outcome().trim().toUpperCase();
        if ("DELIVERED".equals(outcome))
        {
            String method = request.method() == null ? "SELF" : request.method().trim().toUpperCase();
            if (!"SELF".equals(method) && !"PHONE".equals(method))
            {
                throw new ApiException(400, "妥投方式只能是 SELF（本人签收）或 PHONE（电话确认）");
            }
            String remark = "快递员妥投：" + ("SELF".equals(method) ? "本人签收" : "电话确认");
            if (orderMapper.deliverSuccess(order.getOrderNo(), LocalDateTime.now()) == 0)
            {
                throw new ApiException(409, "订单已完成或状态已变化，请勿重复操作");
            }
            if (routeMapper.finishLastMile(order.getId(), order.getForwardStationCount(),
                    RouteStatus.SIGNED, remark) == 0)
            {
                throw new ApiException(409, "妥投失败，路段状态已变化");
            }
            return;
        }

        if (!"FAILED".equals(outcome))
        {
            throw new ApiException(400, "结果只能是 DELIVERED 或 FAILED");
        }
        String reasonCode = request.reason() == null ? "" : request.reason().trim().toUpperCase();
        String reasonText;
        if ("UNREACHABLE".equals(reasonCode))
        {
            reasonText = "联系不上";
        }
        else if ("WRONG_ADDRESS".equals(reasonCode))
        {
            reasonText = "地址有误";
        }
        else
        {
            throw new ApiException(400, "失败原因只能是 UNREACHABLE（联系不上）或 WRONG_ADDRESS（地址有误）");
        }

        String remark = "派送失败：" + reasonText;
        if (orderMapper.deliveryFailed(order.getOrderNo()) == 0)
        {
            throw new ApiException(409, "订单已完成或状态已变化，请勿重复操作");
        }
        // 清空 postman_id 与 depart_time，订单回到目的站“待派”，同一人或他人都能再派
        if (routeMapper.resetLastMile(order.getId(), order.getForwardStationCount(), remark) == 0)
        {
            throw new ApiException(409, "派送失败处理异常，路段状态已变化");
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
            throw new ApiException(409, "当前状态不可操作：" + order.getState());
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
