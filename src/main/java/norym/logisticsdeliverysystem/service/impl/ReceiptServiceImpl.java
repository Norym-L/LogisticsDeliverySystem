package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.Postman;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.mapper.PostmanMapper;
import norym.logisticsdeliverysystem.service.PricingService;
import norym.logisticsdeliverysystem.service.ReceiptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ReceiptServiceImpl implements ReceiptService
{
    private final DeliveryOrderMapper orderMapper;
    private final OrderRouteMapper routeMapper;
    private final PostmanMapper postmanMapper;
    private final PricingService pricingService;

    public ReceiptServiceImpl(DeliveryOrderMapper orderMapper,
                              OrderRouteMapper routeMapper,
                              PostmanMapper postmanMapper,
                              PricingService pricingService)
    {
        this.orderMapper = orderMapper;
        this.routeMapper = routeMapper;
        this.postmanMapper = postmanMapper;
        this.pricingService = pricingService;
    }

    @Override
    @Transactional
    public PriceQuoteView confirmReceipt(String orderNo, Long stationId, Long operatorId,
                                         Long postmanId, ConfirmPriceRequest request)
    {
        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.AWAITING_RECEIPT)
        {
            if (order.getState() == OrderState.APPROVED || order.getState() == OrderState.SENDING
                    || order.getState() == OrderState.DELIVERING)
            {
                throw new ApiException(409, "该订单已确认收件，无需重复操作");
            }
            throw new ApiException(409, "当前状态不可确认收件：" + order.getState());
        }
        if (operatorId == null && postmanId == null)
        {
            throw new ApiException(401, "请先登录");
        }
        if (stationId == null || !Objects.equals(stationId, order.getFromStationId()))
        {
            throw new ApiException(403, "只能由始发站确认收件");
        }
        if (postmanId != null)
        {
            Postman postman = postmanMapper.findById(postmanId);
            if (postman == null)
            {
                throw new ApiException(403, "快递员不存在");
            }
            if (postman.getStatus() != null && postman.getStatus() == 0)
            {
                throw new ApiException(403, "该快递员已离岗");
            }
            if (!Objects.equals(postman.getStationId(), order.getFromStationId()))
            {
                throw new ApiException(403, "只能由始发站快递员确认收件");
            }
        }

        BigDecimal weight = request == null ? null : request.weightKg();
        BigDecimal volume = request == null ? null : request.volumeCm3();
        // 未录入时退回用户申报值；申报值也不完整就必须现场录入
        if (weight == null)
        {
            weight = order.getWeight();
        }
        if (volume == null)
        {
            volume = order.getVolumeCm3();
        }
        if (weight == null || volume == null)
        {
            throw new ApiException(400, "确认收件时请录入体积和质量，以完成最终核价");
        }
        pricingService.validateMeasures(weight, volume);

        LocalDateTime now = LocalDateTime.now();
        PriceQuoteView quote = pricingService.calculate(weight, volume,
                        pricingService.distanceForOrder(order))
                .withEstimatedDays(pricingService.estimateDaysForOrder(order));

        int rows = orderMapper.confirmReceipt(order.getOrderNo(), now, volume, weight,
                quote.freight(), postmanId, operatorId);
        if (rows == 0)
        {
            throw new ApiException(409, "确认收件失败，订单状态已变化");
        }
        if (routeMapper.markArrive(order.getId(), 1, now) == 0)
        {
            throw new ApiException(409, "确认收件失败，始发站路段状态已变化");
        }
        return quote.withConfirmation(order.getOrderNo(), now);
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
