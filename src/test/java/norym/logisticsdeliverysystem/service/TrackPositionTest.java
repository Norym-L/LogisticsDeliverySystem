package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.OrderDetailView;
import norym.logisticsdeliverysystem.dto.OrderFormRequest;
import norym.logisticsdeliverysystem.dto.TrackPositionView;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import norym.logisticsdeliverysystem.support.FlowWorld;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轨迹地图的当前位置判定：按订单状态与 order_route 的实际到发时间计算，
 * 不接车辆 GPS。坐标取自 FlowWorld 中与 seed.sql 一致的 WGS84 值。
 */
class TrackPositionTest
{
    private static final long BEIJING = 1L;
    private static final long TIANJIN = 2L;
    private static final long JINAN = 3L;
    private static final long NANJING = 5L;
    private static final long SHANGHAI = 6L;
    private static final long COURIER = 11L;

    @Test
    void positionFollowsStateMachineFromWaitingToReceived()
    {
        FlowWorld world = FlowWorld.seed();
        String orderNo = createOrder(world, "13700000001", "13700000002");

        // 待审批：没有路线，标在始发站，明确“尚未发运”
        OrderDetailView waiting = world.orderService.detail(orderNo);
        assertTrue(waiting.trajectory().isEmpty());
        TrackPositionView position = waiting.currentPosition();
        assertEquals("NOT_SHIPPED", position.mode());
        assertEquals(0, position.seq());
        assertEquals(BEIJING, position.stationId());
        assertEquals("北京转运中心", position.stationName());
        assertEquals("尚未发运，等待审批", position.label());
        assertNotNull(position.latitude());
        assertEquals(0, new BigDecimal("39.9068844").compareTo(position.latitude()));
        assertNull(position.fromStationId());

        // 批准后：货还没到始发站，但位置属于始发站，不能显示在途
        world.adminOrders.approve(orderNo, 1L, null);
        position = world.orderService.detail(orderNo).currentPosition();
        assertEquals("AT_STATION", position.mode());
        assertEquals(1, position.seq());
        assertEquals(BEIJING, position.stationId());
        assertEquals("在始发站，等待确认收件（北京转运中心）", position.label());

        var approvedDetail = world.orderService.detail(orderNo);
        assertEquals(5, approvedDetail.trajectory().size());
        var firstNode = approvedDetail.trajectory().get(0);
        assertEquals(BEIJING, firstNode.stationId());
        assertNotNull(firstNode.latitude());
        assertNotNull(firstNode.longitude());
        assertEquals(0, new BigDecimal("39.9068844").compareTo(firstNode.latitude()));

        // 始发站确认收件并核价：仍在始发站等发运
        world.receipts.confirmReceipt(orderNo, BEIJING, 2L, null,
                new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50")));
        position = world.orderService.detail(orderNo).currentPosition();
        assertEquals("AT_STATION", position.mode());
        assertEquals("在始发站，等待发运（北京转运中心）", position.label());

        // 首次发运、下一站未到：标记在相邻两站中点
        world.adminOrders.assignTrip(orderNo, BEIJING, "T1");
        world.adminOrders.depart(orderNo, BEIJING);
        assertEquals(OrderState.SENDING, world.orderState(orderNo));
        position = world.orderService.detail(orderNo).currentPosition();
        assertEquals("IN_TRANSIT", position.mode());
        assertEquals(1, position.seq());
        assertEquals(BEIJING, position.stationId());
        assertEquals("运输中：北京转运中心 → 天津转运中心", position.label());
        assertEquals("北京转运中心", position.fromStationName());
        assertEquals("天津转运中心", position.toStationName());
        assertEquals(0, new BigDecimal("39.5573380").compareTo(position.latitude()));
        assertEquals(0, new BigDecimal("116.8100624").compareTo(position.longitude()));

        // 确认到站：位置回到天津站，下一段尚未发运
        world.adminOrders.arrive(orderNo, TIANJIN);
        position = world.orderService.detail(orderNo).currentPosition();
        assertEquals("AT_STATION", position.mode());
        assertEquals(2, position.seq());
        assertEquals(TIANJIN, position.stationId());
        assertEquals("在站等待发运（天津转运中心）", position.label());

        // 继续逐站推进到目的站
        hop(world, orderNo, TIANJIN, "T2", JINAN);
        hop(world, orderNo, JINAN, "T3", NANJING);
        hop(world, orderNo, NANJING, "T4", SHANGHAI);

        // 到目的站后订单仍是 SENDING（等快递员取件），位置在目的站
        assertEquals(OrderState.SENDING, world.orderState(orderNo));
        position = world.orderService.detail(orderNo).currentPosition();
        assertEquals("AT_STATION", position.mode());
        assertEquals(5, position.seq());
        assertEquals(SHANGHAI, position.stationId());
        assertEquals("已到目的站，等待取件派送（上海转运中心）", position.label());

        // 派送中：仍在目的站，不是移动中的干线运输
        world.adminOrders.assignCourier(orderNo, SHANGHAI, COURIER);
        world.postmanOrders.pickup(orderNo, COURIER, null);
        assertEquals(OrderState.DELIVERING, world.orderState(orderNo));
        position = world.orderService.detail(orderNo).currentPosition();
        assertEquals("DELIVERING", position.mode());
        assertEquals(SHANGHAI, position.stationId());
        assertEquals("派送中（最后一公里） · 上海转运中心", position.label());

        // 签收：停在目的站，不再显示在途
        world.orderService.sign(orderNo, "13700000002");
        assertEquals(OrderState.RECEIVED, world.orderState(orderNo));
        position = world.orderService.detail(orderNo).currentPosition();
        assertEquals("RECEIVED", position.mode());
        assertEquals(5, position.seq());
        assertEquals(SHANGHAI, position.stationId());
        assertEquals("已签收（上海转运中心）", position.label());
        assertNull(position.fromStationId());
    }

    @Test
    void cancelledStaysAtOriginAndRefusedStaysAtDestination()
    {
        FlowWorld world = FlowWorld.seed();

        // 未发运即取消：即使已有路线，也标在始发站并说明已取消
        String cancelled = createOrder(world, "13700000011", "13700000012");
        world.adminOrders.approve(cancelled, 1L, null);
        world.orderService.cancel(cancelled, "13700000011");
        assertEquals(OrderState.CANCELLED, world.orderState(cancelled));
        TrackPositionView cancelledPosition = world.orderService.detail(cancelled).currentPosition();
        assertEquals("NOT_SHIPPED", cancelledPosition.mode());
        assertEquals(BEIJING, cancelledPosition.stationId());
        assertEquals("尚未发运（订单已取消）", cancelledPosition.label());

        // 拒收：原单在目的站终结，生成方向相反的退回单
        String refused = createOrder(world, "13700000013", "13700000014");
        world.adminOrders.approve(refused, 1L, null);
        world.receipts.confirmReceipt(refused, BEIJING, 2L, null,
                new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50")));
        hop(world, refused, BEIJING, "T1", TIANJIN);
        hop(world, refused, TIANJIN, "T2", JINAN);
        hop(world, refused, JINAN, "T3", NANJING);
        hop(world, refused, NANJING, "T4", SHANGHAI);
        world.adminOrders.assignCourier(refused, SHANGHAI, COURIER);
        world.postmanOrders.pickup(refused, COURIER, null);
        world.orderService.refuse(refused, "13700000014", "收件人拒收");

        assertEquals(OrderState.REFUSED, world.orderState(refused));
        OrderDetailView refusedDetail = world.orderService.detail(refused);
        assertEquals(RouteStatus.REFUSED, world.routeStatus(refused, 5));
        assertEquals("REFUSED", refusedDetail.currentPosition().mode());
        assertEquals(SHANGHAI, refusedDetail.currentPosition().stationId());
        assertEquals("已拒收（上海转运中心）", refusedDetail.currentPosition().label());
        assertNotNull(refusedDetail.returnOrderNo());
    }

    private static void hop(FlowWorld world, String orderNo, long from, String tripNo, long to)
    {
        world.adminOrders.assignTrip(orderNo, from, tripNo);
        world.adminOrders.depart(orderNo, from);
        world.adminOrders.arrive(orderNo, to);
    }

    private static String createOrder(FlowWorld world, String senderPhone, String receiverPhone)
    {
        return world.orderService.create(new OrderFormRequest(
                "张三", senderPhone, "李四", receiverPhone,
                BEIJING, SHANGHAI, "教材", new BigDecimal("1.50"), null)).orderNo();
    }
}
