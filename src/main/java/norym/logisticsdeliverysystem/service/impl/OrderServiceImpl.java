package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.CreatedOrderView;
import norym.logisticsdeliverysystem.dto.FieldLimits;
import norym.logisticsdeliverysystem.dto.OrderBriefView;
import norym.logisticsdeliverysystem.dto.OrderDetailView;
import norym.logisticsdeliverysystem.dto.OrderFormRequest;
import norym.logisticsdeliverysystem.dto.OrderStatusView;
import norym.logisticsdeliverysystem.dto.QrCodeView;
import norym.logisticsdeliverysystem.dto.RouteNodeView;
import norym.logisticsdeliverysystem.dto.StationView;
import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import norym.logisticsdeliverysystem.entity.OrderRoute;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import norym.logisticsdeliverysystem.entity.Station;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import norym.logisticsdeliverysystem.mapper.OrderRouteMapper;
import norym.logisticsdeliverysystem.mapper.StationMapper;
import norym.logisticsdeliverysystem.service.OrderService;
import norym.logisticsdeliverysystem.service.PricingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService
{
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ORDER_NO_PREFIX = "LD";
    /** 单号里保留的 UUID 十六进制位数：12 位 = 48 bit 随机量 */
    private static final int ORDER_NO_UUID_LENGTH = 12;
    private static final int ORDER_NO_RETRY = 3;

    private final DeliveryOrderMapper orderMapper;
    private final OrderRouteMapper routeMapper;
    private final StationMapper stationMapper;
    private final PricingService pricingService;
    private final int approveTimeoutSeconds;
    private final int maxResubmit;

    public OrderServiceImpl(DeliveryOrderMapper orderMapper,
                            OrderRouteMapper routeMapper,
                            StationMapper stationMapper,
                            PricingService pricingService,
                            @Value("${logistics.approve-timeout-seconds:60}") int approveTimeoutSeconds,
                            @Value("${logistics.max-resubmit:3}") int maxResubmit)
    {
        this.orderMapper = orderMapper;
        this.routeMapper = routeMapper;
        this.stationMapper = stationMapper;
        this.pricingService = pricingService;
        this.approveTimeoutSeconds = approveTimeoutSeconds;
        this.maxResubmit = maxResubmit;
    }

    @Override
    @Transactional
    public CreatedOrderView create(OrderFormRequest request)
    {
        validateForm(request);

        LocalDateTime now = LocalDateTime.now();
        DeliveryOrder order = new DeliveryOrder();
        fillForm(order, request);
        order.setState(OrderState.WAITING);
        order.setApproveDeadline(now.plusSeconds(approveTimeoutSeconds));
        insertWithRetry(order);
        return toCreated(order);
    }

    /** 取号并插入；极低概率撞号时换一个随机串重试 */
    private void insertWithRetry(DeliveryOrder order)
    {
        for (int attempt = 0; attempt < ORDER_NO_RETRY; attempt++)
        {
            order.setOrderNo(nextOrderNo());
            try
            {
                orderMapper.insert(order);
                return;
            }
            catch (DuplicateKeyException e)
            {
                // 撞号了，重新取号
            }
        }
        throw new ApiException(500, "订单号生成冲突，请重试");
    }

    @Override
    public List<OrderBriefView> listByPhone(String phone)
    {
        if (isBlank(phone))
        {
            throw new ApiException(400, "手机号不能为空");
        }

        Map<Long, Station> stations = loadStations();
        List<OrderBriefView> result = new ArrayList<>();
        for (DeliveryOrder order : orderMapper.findByPhone(phone.trim()))
        {
            result.add(new OrderBriefView(
                    order.getOrderNo(),
                    order.getState() == null ? null : order.getState().name(),
                    stationName(stations, order.getFromStationId()),
                    stationName(stations, order.getToStationId()),
                    order.getCreateTime(),
                    order.getApproveDeadline(),
                    order.getQuotedPrice(),
                    order.getPrice(),
                    order.isPriceConfirmed()));
        }
        return result;
    }

    @Override
    public OrderDetailView detail(String orderNo)
    {
        DeliveryOrder order = requireOrder(orderNo);
        Map<Long, Station> stations = loadStations();

        List<RouteNodeView> trajectory = new ArrayList<>();
        for (OrderRoute route : routeMapper.findByOrderId(order.getId()))
        {
            trajectory.add(new RouteNodeView(
                    route.getSeq(),
                    stationName(stations, route.getStationId()),
                    route.getStatus() == null ? null : route.getStatus().name(),
                    route.getDepartTime(),
                    route.getActualArriveTime(),
                    route.getArriveTripNo()));
        }
        DeliveryOrder returnOrder = orderMapper.findByReturnOf(order.getOrderNo());

        return new OrderDetailView(
                order.getOrderNo(),
                order.getState() == null ? null : order.getState().name(),
                order.getSenderName(), order.getSenderPhone(),
                order.getReceiverName(), order.getReceiverPhone(),
                toView(stations, order.getFromStationId()),
                toView(stations, order.getToStationId()),
                toView(stations, order.getCurrentStationId()),
                order.getGoodsDesc(), order.getWeight(),
                order.getVolumeCm3(),
                order.getActualWeight(),
                order.getActualVolumeCm3(),
                order.getQuotedPrice(),
                order.getPrice(),
                order.isPriceConfirmed(),
                order.getPriceConfirmedAt(),
                trajectory,
                new OrderDetailView.Events(order.getCreateTime(), order.getApproveTime(),
                        order.getReceiveTime(), order.getRefuseTime()),
                order.getRejectReason(),
                order.getRefuseReason(),
                returnOrder == null ? null : returnOrder.getOrderNo(),
                order.getReturnOf());
    }

    @Override
    public OrderStatusView status(String orderNo)
    {
        DeliveryOrder order = requireOrder(orderNo);
        long remaining = 0L;
        if (order.getState() == OrderState.WAITING && order.getApproveDeadline() != null)
        {
            remaining = Math.max(0L, Duration.between(LocalDateTime.now(), order.getApproveDeadline()).getSeconds());
        }
        return new OrderStatusView(order.getOrderNo(),
                order.getState() == null ? null : order.getState().name(),
                order.getRejectReason(), order.getApproveDeadline(), remaining);
    }

    @Override
    @Transactional
    public void sign(String orderNo, String receiverPhone)
    {
        if (isBlank(receiverPhone))
        {
            throw new ApiException(400, "收件人手机号不能为空");
        }

        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.DELIVERING)
        {
            throw new ApiException(409, "当前状态不可签收：" + order.getState());
        }
        if (!receiverPhone.trim().equals(order.getReceiverPhone()))
        {
            throw new ApiException(403, "手机号与收件人不一致");
        }

        int rows = orderMapper.sign(orderNo, receiverPhone.trim(), LocalDateTime.now());
        if (rows == 0)
        {
            throw new ApiException(409, "签收失败，订单状态已变化");
        }
        routeMapper.updateStatus(order.getId(), order.getCurrentSeq(), RouteStatus.SIGNED);
    }

    @Override
    @Transactional
    public CreatedOrderView refuse(String orderNo, String receiverPhone, String reason)
    {
        if (isBlank(receiverPhone))
        {
            throw new ApiException(400, "收件人手机号不能为空");
        }

        DeliveryOrder order = requireOrder(orderNo);
        if (order.getState() != OrderState.DELIVERING)
        {
            throw new ApiException(409, "当前状态不可拒收：" + order.getState());
        }
        if (!receiverPhone.trim().equals(order.getReceiverPhone()))
        {
            throw new ApiException(403, "手机号与收件人不一致");
        }

        String finalReason = isBlank(reason) ? "收件人拒收" : reason.trim();
        requireMaxLength(finalReason, FieldLimits.REASON, "拒收原因");
        int rows = orderMapper.refuse(orderNo, receiverPhone.trim(), finalReason, LocalDateTime.now());
        if (rows == 0)
        {
            throw new ApiException(409, "拒收失败，订单状态已变化");
        }

        routeMapper.updateStatus(order.getId(), order.getCurrentSeq(), RouteStatus.REFUSED);
        return createReturnOrder(order);
    }

    @Override
    @Transactional
    public void resubmit(String orderNo, OrderFormRequest request)
    {
        validateForm(request);

        DeliveryOrder existing = requireOrder(orderNo);
        if (existing.getState() != OrderState.REJECTED)
        {
            throw new ApiException(409, "只有被拒绝的订单可以修改重提");
        }
        if (!request.senderPhone().trim().equals(existing.getSenderPhone()))
        {
            throw new ApiException(403, "手机号与寄件人不一致");
        }
        if (!isBlank(existing.getReturnOf()))
        {
            requireSameDirection(existing, request);
        }
        int used = existing.getResubmitCount() == null ? 0 : existing.getResubmitCount();
        if (used >= maxResubmit)
        {
            throw new ApiException(409, "重提次数已达上限 " + maxResubmit);
        }

        DeliveryOrder update = new DeliveryOrder();
        update.setOrderNo(orderNo);
        fillForm(update, request);

        int rows = orderMapper.resubmit(update,
                LocalDateTime.now().plusSeconds(approveTimeoutSeconds), maxResubmit);
        if (rows == 0)
        {
            throw new ApiException(409, "重提失败，订单状态已变化");
        }
    }

    @Override
    public QrCodeView qrCode(String orderNo)
    {
        DeliveryOrder order = requireOrder(orderNo);
        return new QrCodeView(order.getOrderNo(), order.getOrderNo(), "ORDER_NO");
    }

    /**
     * 生成退回单：方向相反的独立订单，走同一套审批与运输流程。
     * <p>
     * 寄件人与收件人对调——原收件人把货寄回，原寄件人收货。
     * 用户不需要重新填写收发信息，这正是"拒收流程与发起订单相似，
     * 只省掉填写收寄信息那一步"的落地方式。
     */
    private CreatedOrderView createReturnOrder(DeliveryOrder original)
    {
        DeliveryOrder back = new DeliveryOrder();
        back.setSenderName(original.getReceiverName());
        back.setSenderPhone(original.getReceiverPhone());
        back.setReceiverName(original.getSenderName());
        back.setReceiverPhone(original.getSenderPhone());
        back.setFromStationId(original.getToStationId());
        back.setToStationId(original.getFromStationId());
        back.setGoodsDesc(original.getGoodsDesc());
        back.setWeight(original.getWeight());
        back.setVolumeCm3(original.getVolumeCm3());
        back.setQuotedPrice(estimateQuotedPrice(
                back.getFromStationId(), back.getToStationId(),
                back.getWeight(), back.getVolumeCm3()));
        back.setState(OrderState.WAITING);
        back.setApproveDeadline(LocalDateTime.now().plusSeconds(approveTimeoutSeconds));
        back.setReturnOf(original.getOrderNo());

        insertWithRetry(back);
        return toCreated(back);
    }

    private DeliveryOrder requireOrder(String orderNo)
    {
        if (isBlank(orderNo))
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

    private void validateForm(OrderFormRequest request)
    {
        if (request == null)
        {
            throw new ApiException(400, "请求体不能为空");
        }
        requireText(request.senderName(), "寄件人姓名", FieldLimits.NAME);
        requireText(request.receiverName(), "收件人姓名", FieldLimits.NAME);
        requirePhone(request.senderPhone(), "寄件人手机号");
        requirePhone(request.receiverPhone(), "收件人手机号");
        if (request.goodsDesc() != null && request.goodsDesc().trim().length() > FieldLimits.GOODS)
        {
            throw new ApiException(400, "货物描述不能超过 " + FieldLimits.GOODS + " 个字");
        }

        if (request.fromStationId() == null || request.toStationId() == null)
        {
            throw new ApiException(400, "始发站和目的站不能为空");
        }
        if (request.fromStationId().equals(request.toStationId()))
        {
            throw new ApiException(400, "始发站与目的站不能相同");
        }
        if (stationMapper.findById(request.fromStationId()) == null)
        {
            throw new ApiException(400, "始发站不存在");
        }
        if (stationMapper.findById(request.toStationId()) == null)
        {
            throw new ApiException(400, "目的站不存在");
        }
        if (request.weight() != null)
        {
            if (request.weight().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ApiException(400, "重量不能为负");
            }
            if (request.weight().compareTo(FieldLimits.MAX_WEIGHT) > 0)
            {
                throw new ApiException(400, "重量超出范围");
            }
        }
        if (request.volumeCm3() != null)
        {
            if (request.volumeCm3().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ApiException(400, "体积不能为负");
            }
            if (request.volumeCm3().compareTo(FieldLimits.MAX_VOLUME) > 0)
            {
                throw new ApiException(400, "体积超出范围");
            }
        }
    }

    private void fillForm(DeliveryOrder order, OrderFormRequest request)
    {
        order.setSenderName(request.senderName().trim());
        order.setSenderPhone(request.senderPhone().trim());
        order.setReceiverName(request.receiverName().trim());
        order.setReceiverPhone(request.receiverPhone().trim());
        order.setFromStationId(request.fromStationId());
        order.setToStationId(request.toStationId());
        order.setGoodsDesc(isBlank(request.goodsDesc()) ? null : request.goodsDesc().trim());
        order.setWeight(request.weight());
        order.setVolumeCm3(request.volumeCm3());
        order.setQuotedPrice(estimateQuotedPrice(
                request.fromStationId(), request.toStationId(),
                request.weight(), request.volumeCm3()));
    }

    /**
     * 质量和体积都大于 0 时，按最短路给出预估价；只填其中一项则不算价。
     */
    private BigDecimal estimateQuotedPrice(Long fromStationId, Long toStationId,
                                           BigDecimal weight, BigDecimal volumeCm3)
    {
        if (weight == null || volumeCm3 == null)
        {
            return null;
        }
        if (weight.compareTo(BigDecimal.ZERO) <= 0 || volumeCm3.compareTo(BigDecimal.ZERO) <= 0)
        {
            return null;
        }
        return pricingService.quoteShortest(fromStationId, toStationId, weight, volumeCm3).freight();
    }

    private CreatedOrderView toCreated(DeliveryOrder order)
    {
        return new CreatedOrderView(
                order.getOrderNo(),
                order.getState() == null ? null : order.getState().name(),
                order.getApproveDeadline(),
                order.getVolumeCm3(),
                order.getQuotedPrice(),
                order.getPrice(),
                order.isPriceConfirmed());
    }

    /** 退回单的收发件人和起止站必须保持对调结果，只允许改货物信息 */
    private void requireSameDirection(DeliveryOrder existing, OrderFormRequest request)
    {
        boolean same = sameText(existing.getSenderName(), request.senderName())
                && sameText(existing.getSenderPhone(), request.senderPhone())
                && sameText(existing.getReceiverName(), request.receiverName())
                && sameText(existing.getReceiverPhone(), request.receiverPhone())
                && existing.getFromStationId().equals(request.fromStationId())
                && existing.getToStationId().equals(request.toStationId());
        if (!same)
        {
            throw new ApiException(400, "退回单不能修改收发件人和起止站");
        }
    }

    private void requireText(String value, String field, int max)
    {
        if (isBlank(value))
        {
            throw new ApiException(400, field + "不能为空");
        }
        requireMaxLength(value.trim(), max, field);
    }

    private void requireMaxLength(String value, int max, String field)
    {
        if (value != null && value.length() > max)
        {
            throw new ApiException(400, field + "不能超过 " + max + " 个字");
        }
    }

    private boolean sameText(String left, String right)
    {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    private void requirePhone(String phone, String field)
    {
        if (isBlank(phone) || !phone.trim().matches("^1\\d{10}$"))
        {
            throw new ApiException(400, field + "格式不正确（11 位手机号）");
        }
    }

    /**
     * 单号规则：LD + yyyyMMdd + UUID 前 12 位十六进制，共 22 个字符。
     * <p>
     * 截断到 48 bit 而不是完整 122 bit，是长度和碰撞概率的折中：
     * 同一天内约 2000 万单才有 50% 概率出现一次重复，远超实际量级。
     * 日期前缀保留可读性，按日期范围检索也能直接走索引。
     * <p>
     * 唯一键 uk_order_no 仍然保留：随机量是概率保证，唯一键是确定性保证。
     * 真撞上了插入会失败，重试换一个随机串，不会产生两条同号订单。
     */
    private String nextOrderNo()
    {
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return ORDER_NO_PREFIX
                + LocalDateTime.now().format(DAY_FORMAT)
                + hex.substring(0, ORDER_NO_UUID_LENGTH);
    }

    private Map<Long, Station> loadStations()
    {
        return stationMapper.findAll().stream()
                .collect(Collectors.toMap(Station::getId, Function.identity()));
    }

    private String stationName(Map<Long, Station> stations, Long stationId)
    {
        StationView view = toView(stations, stationId);
        return view == null ? null : view.name();
    }

    private StationView toView(Map<Long, Station> stations, Long stationId)
    {
        if (stationId == null)
        {
            return null;
        }
        Station station = stations.get(stationId);
        if (station == null)
        {
            station = stationMapper.findById(stationId);
        }
        return station == null ? null : new StationView(station.getId(), station.getCode(), station.getName());
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
