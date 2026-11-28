package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.config.PricingProperties;
import norym.logisticsdeliverysystem.dto.FieldLimits;
import norym.logisticsdeliverysystem.dto.PriceQuoteRequest;
import norym.logisticsdeliverysystem.dto.PriceQuoteView;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.service.PricingCalculator;
import norym.logisticsdeliverysystem.service.PricingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class PricingServiceImpl implements PricingService
{
    private final PricingCalculator calculator;
    private final RoutePlanner planner;
    private final DeliveryOrderMapper orderMapper;
    private final OrderRouteMapper routeMapper;

    public PricingServiceImpl(PricingProperties properties,
                              RoutePlanner planner,
                              DeliveryOrderMapper orderMapper,
                              OrderRouteMapper routeMapper)
    {
        this.calculator = new PricingCalculator(properties);
        this.planner = planner;
        this.orderMapper = orderMapper;
        this.routeMapper = routeMapper;
    }

    @Override
    public PriceQuoteView quote(PriceQuoteRequest request)
    {
        if (request == null)
        {
            throw new ApiException(400, "请求体不能为空");
        }
        validateMeasures(request.weightKg(), request.volumeCm3());

        boolean hasOrderNo = request.orderNo() != null && !request.orderNo().isBlank();
        boolean hasStations = request.fromStationId() != null && request.toStationId() != null;
        if (!hasOrderNo && !hasStations)
        {
            throw new ApiException(400, "请提供起止站或订单号以计算里程");
        }
        if (!hasOrderNo && (request.fromStationId() == null || request.toStationId() == null))
        {
            throw new ApiException(400, "始发站和目的站需要同时提供");
        }

        if (hasOrderNo)
        {
            DeliveryOrder order = orderMapper.findByOrderNo(request.orderNo().trim());
            if (order == null)
            {
                throw new ApiException(404, "订单不存在：" + request.orderNo());
            }
            List<Long> path = pathForOrder(order);
            PriceQuoteView quote = calculator.calculate(
                    request.weightKg(), request.volumeCm3(), planner.distanceOf(path));
            return new PriceQuoteView(
                    quote.weightKg(),
                    quote.volumeCm3(),
                    quote.volumetricWeightKg(),
                    quote.chargeableWeightKg(),
                    quote.distanceKm(),
                    quote.firstKgPrice(),
                    quote.additionalKgPrice(),
                    quote.additionalWeightKg(),
                    quote.freight(),
                    estimateDays(path),
                    order.getOrderNo(),
                    null,
                    null);
        }
        return quoteShortest(request.fromStationId(), request.toStationId(),
                request.weightKg(), request.volumeCm3());
    }

    @Override
    public PriceQuoteView quoteShortest(Long fromStationId, Long toStationId,
                                        BigDecimal weightKg, BigDecimal volumeCm3)
    {
        validateMeasures(weightKg, volumeCm3);
        List<Long> path = planner.shortestPath(fromStationId, toStationId);
        PriceQuoteView quote = calculator.calculate(weightKg, volumeCm3, planner.distanceOf(path));
        return quote.withEstimatedDays(estimateDays(path));
    }

    @Override
    public PriceQuoteView calculate(BigDecimal weightKg, BigDecimal volumeCm3, BigDecimal distanceKm)
    {
        validateMeasures(weightKg, volumeCm3);
        return calculator.calculate(weightKg, volumeCm3, distanceKm);
    }

    @Override
    public BigDecimal distanceForOrder(DeliveryOrder order)
    {
        return planner.distanceOf(pathForOrder(order));
    }

    @Override
    public int estimateDaysForOrder(DeliveryOrder order)
    {
        return estimateDays(pathForOrder(order));
    }

    @Override
    public int estimateDaysForStations(Long fromStationId, Long toStationId)
    {
        return estimateDays(planner.shortestPath(fromStationId, toStationId));
    }

    /** 有固化路线时用路线，否则用最短路；空路线按 1 站处理 */
    private List<Long> pathForOrder(DeliveryOrder order)
    {
        if (order == null)
        {
            throw new ApiException(400, "订单不能为空");
        }
        List<OrderRoute> legs = routeMapper.findByOrderId(order.getId());
        if (legs != null && legs.size() >= 2)
        {
            return legs.stream()
                    .sorted(Comparator.comparing(OrderRoute::getSeq))
                    .map(OrderRoute::getStationId)
                    .toList();
        }
        return planner.shortestPath(order.getFromStationId(), order.getToStationId());
    }

    /** 每跨一站记 1 日，最少 1 日；规则简单、可复现，不接外部路由预测 */
    private int estimateDays(List<Long> path)
    {
        int hops = path == null ? 0 : path.size() - 1;
        return Math.max(1, hops);
    }

    @Override
    public void validateMeasures(BigDecimal weightKg, BigDecimal volumeCm3)
    {
        if (weightKg == null)
        {
            throw new ApiException(400, "质量不能为空");
        }
        if (volumeCm3 == null)
        {
            throw new ApiException(400, "体积不能为空");
        }
        if (weightKg.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ApiException(400, "质量必须大于 0");
        }
        if (volumeCm3.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ApiException(400, "体积必须大于 0");
        }
        if (weightKg.compareTo(FieldLimits.MAX_WEIGHT) > 0)
        {
            throw new ApiException(400, "质量超出范围");
        }
        if (volumeCm3.compareTo(FieldLimits.MAX_VOLUME) > 0)
        {
            throw new ApiException(400, "体积超出范围");
        }
    }
}
