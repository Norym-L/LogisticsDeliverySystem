package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.ConfirmPriceRequest;
import norym.logisticsdeliverysystem.dto.DeliverRequest;
import norym.logisticsdeliverysystem.dto.OrderFormRequest;
import norym.logisticsdeliverysystem.dto.PostmanTask;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.entity.RouteStatus;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.support.FlowWorld;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 走服务层的主流程。不启动 Spring，也不连接 MySQL。
 */
class DeliveryFlowTest
{
    private static final long BEIJING = 1L;
    private static final long TIANJIN = 2L;
    private static final long JINAN = 3L;
    private static final long NANJING = 5L;
    private static final long SHANGHAI = 6L;
    private static final long ORIGIN_COURIER = 1L;
    private static final long COURIER = 11L;

    @Test
    void mainFlowRequiresOriginReceiptPricingThenDelivery()
    {
        FlowWorld world = FlowWorld.seed();
        String orderNo = world.orderService.create(form("13700000001", "13700000002", "教材")).orderNo();

        world.adminOrders.approve(orderNo, 1L, null);
        var detail = world.orderService.detail(orderNo);
        assertEquals(OrderState.AWAITING_RECEIPT, world.orderState(orderNo));
        assertEquals(List.of("北京转运中心", "天津转运中心", "济南转运中心", "南京转运中心", "上海转运中心"),
                detail.trajectory().stream().map(n -> n.stationName()).toList());
        // 批准后不再把始发站视为已到站，必须由始发站确认收件
        assertEquals(RouteStatus.PENDING.name(), detail.trajectory().get(0).status());
        assertEquals(RouteStatus.PENDING.name(), detail.trajectory().get(1).status());

        ApiException beforeReceipt = assertThrows(ApiException.class,
                () -> world.adminOrders.assignTrip(orderNo, BEIJING, "T1"));
        assertEquals(409, beforeReceipt.getCode());

        ApiException wrongStation = assertThrows(ApiException.class,
                () -> world.receipts.confirmReceipt(orderNo, TIANJIN, 2L, null,
                        new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50"))));
        assertEquals(403, wrongStation.getCode());

        var receiptQuote = world.receipts.confirmReceipt(orderNo, BEIJING, 2L, null,
                new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50")));
        assertEquals(0, new BigDecimal("33").compareTo(receiptQuote.freight()));
        assertEquals(OrderState.APPROVED, world.orderState(orderNo));
        assertEquals(RouteStatus.ARRIVED, world.routeStatus(orderNo, 1));
        assertEquals(0, new BigDecimal("33").compareTo(world.orderService.detail(orderNo).price()));

        ApiException repeatReceipt = assertThrows(ApiException.class,
                () -> world.receipts.confirmReceipt(orderNo, BEIJING, 2L, null,
                        new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50"))));
        assertEquals(409, repeatReceipt.getCode());

        ApiException beforeDepart = assertThrows(ApiException.class, () -> world.adminOrders.arrive(orderNo, TIANJIN));
        assertEquals(409, beforeDepart.getCode());

        world.adminOrders.assignTrip(orderNo, BEIJING, "T1");
        world.adminOrders.depart(orderNo, BEIJING);
        world.adminOrders.arrive(orderNo, TIANJIN);

        ApiException skipped = assertThrows(ApiException.class, () -> world.adminOrders.arrive(orderNo, JINAN));
        assertEquals(409, skipped.getCode());
        assertTrue(skipped.getMessage().contains("尚未发运"));

        world.adminOrders.assignTrip(orderNo, TIANJIN, "T2");
        world.adminOrders.depart(orderNo, TIANJIN);
        world.clearTrip(orderNo, 3);
        ApiException missingTrip = assertThrows(ApiException.class, () -> world.adminOrders.arrive(orderNo, JINAN));
        assertEquals(400, missingTrip.getCode());
        assertTrue(missingTrip.getMessage().contains("车次"));
        world.setTrip(orderNo, 3, "T2");
        world.adminOrders.arrive(orderNo, JINAN);

        world.adminOrders.assignTrip(orderNo, JINAN, "T3");
        world.adminOrders.depart(orderNo, JINAN);
        world.adminOrders.arrive(orderNo, NANJING);

        world.adminOrders.assignTrip(orderNo, NANJING, "T4");
        world.adminOrders.depart(orderNo, NANJING);
        world.adminOrders.arrive(orderNo, SHANGHAI);

        world.adminOrders.assignCourier(orderNo, SHANGHAI, COURIER);
        assertEquals(OrderState.SENDING, world.orderState(orderNo));
        assertTrue(orderNos(world.adminPostmen.tasks(COURIER, "pending", null)).contains(orderNo));
        assertTrue(orderNos(world.adminPostmen.tasks(COURIER, "completed", null)).isEmpty());

        // 目的站只能只读复核最终价，不能改价
        var reviewed = world.postmanOrders.confirmPrice(orderNo, COURIER,
                new ConfirmPriceRequest(new BigDecimal("1"), new BigDecimal("999")));
        assertEquals(0, new BigDecimal("33").compareTo(reviewed.freight()));
        assertEquals(0, new BigDecimal("33").compareTo(world.orderService.detail(orderNo).price()));

        world.postmanOrders.pickup(orderNo, COURIER, null);
        assertEquals(OrderState.DELIVERING, world.orderState(orderNo));
        assertEquals(RouteStatus.DELIVERING, world.routeStatus(orderNo, 5));
        assertTrue(orderNos(world.adminPostmen.tasks(COURIER, "pending", null)).contains(orderNo));

        world.orderService.sign(orderNo, "13700000002");
        assertEquals(OrderState.RECEIVED, world.orderState(orderNo));
        assertEquals(RouteStatus.SIGNED, world.routeStatus(orderNo, 5));
        assertTrue(orderNos(world.adminPostmen.tasks(COURIER, "pending", null)).isEmpty());
        assertTrue(orderNos(world.adminPostmen.tasks(COURIER, "completed", null)).contains(orderNo));

        ApiException repeatSign = assertThrows(ApiException.class,
                () -> world.orderService.sign(orderNo, "13700000002"));
        assertEquals(409, repeatSign.getCode());
    }

    @Test
    void originPostmanCanReceiveAndTaskLookupRejectsOthers()
    {
        FlowWorld world = FlowWorld.seed();
        String orderNo = world.orderService.create(form("13700000001", "13700000002", "教材")).orderNo();
        world.adminOrders.approve(orderNo, 1L, null);

        List<PostmanTask> originTasks = world.postmanOrders.tasks(ORIGIN_COURIER, BEIJING, "pending");
        assertEquals(1, originTasks.size());
        assertEquals("RECEIVE", originTasks.get(0).getTaskType());
        assertEquals(orderNo, world.postmanOrders.taskByOrderNo(orderNo, ORIGIN_COURIER, BEIJING).getOrderNo());

        ApiException otherCourier = assertThrows(ApiException.class,
                () -> world.postmanOrders.taskByOrderNo(orderNo, COURIER, SHANGHAI));
        assertEquals(403, otherCourier.getCode());

        world.receipts.confirmReceipt(orderNo, BEIJING, null, ORIGIN_COURIER,
                new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50")));
        assertEquals(OrderState.APPROVED, world.orderState(orderNo));
        assertTrue(world.postmanOrders.tasks(ORIGIN_COURIER, BEIJING, "pending").isEmpty());

        ApiException afterReceipt = assertThrows(ApiException.class,
                () -> world.postmanOrders.taskByOrderNo(orderNo, ORIGIN_COURIER, BEIJING));
        assertEquals(409, afterReceipt.getCode());
    }

    @Test
    void cancelOnlyBeforeFirstDeparture()
    {
        FlowWorld world = FlowWorld.seed();
        String waiting = world.orderService.create(form("13700000001", "13700000002", "教材")).orderNo();

        ApiException wrongPhone = assertThrows(ApiException.class,
                () -> world.orderService.cancel(waiting, "13700000009"));
        assertEquals(403, wrongPhone.getCode());

        world.orderService.cancel(waiting, "13700000001");
        assertEquals(OrderState.CANCELLED, world.orderState(waiting));
        ApiException repeat = assertThrows(ApiException.class,
                () -> world.orderService.cancel(waiting, "13700000001"));
        assertEquals(409, repeat.getCode());

        // 已批准、待始发收件，仍可取消
        String awaiting = world.orderService.create(form("13700000003", "13700000004", "另一件")).orderNo();
        world.adminOrders.approve(awaiting, 1L, null);
        world.orderService.cancel(awaiting, "13700000003");
        assertEquals(OrderState.CANCELLED, world.orderState(awaiting));

        // 已收件核价、尚未首次发运，仍可取消
        String approved = world.orderService.create(form("13700000005", "13700000006", "第三件")).orderNo();
        world.adminOrders.approve(approved, 1L, null);
        world.receipts.confirmReceipt(approved, BEIJING, 2L, null,
                new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50")));
        world.orderService.cancel(approved, "13700000005");
        assertEquals(OrderState.CANCELLED, world.orderState(approved));

        // 首次发运后不可取消
        String shipped = world.orderService.create(form("13700000007", "13700000008", "第四件")).orderNo();
        world.adminOrders.approve(shipped, 1L, null);
        world.receipts.confirmReceipt(shipped, BEIJING, 2L, null,
                new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50")));
        world.adminOrders.assignTrip(shipped, BEIJING, "T1");
        world.adminOrders.depart(shipped, BEIJING);
        assertEquals(OrderState.SENDING, world.orderState(shipped));
        ApiException tooLate = assertThrows(ApiException.class,
                () -> world.orderService.cancel(shipped, "13700000007"));
        assertEquals(409, tooLate.getCode());
        assertTrue(tooLate.getMessage().contains("不可取消"));
    }

    @Test
    void courierDeliveryAndFailureCoexistWithUserSigning()
    {
        Delivering delivered = deliveringWorld("13700000001", "13700000002");
        String orderNo = delivered.orderNos().get(0);
        assertEquals(OrderState.DELIVERING, delivered.world().orderState(orderNo));

        delivered.world().postmanOrders.deliver(orderNo, COURIER,
                new DeliverRequest("DELIVERED", "PHONE", null));
        assertEquals(OrderState.RECEIVED, delivered.world().orderState(orderNo));
        assertEquals(RouteStatus.SIGNED, delivered.world().routeStatus(orderNo, 5));

        ApiException duplicate = assertThrows(ApiException.class,
                () -> delivered.world().postmanOrders.deliver(orderNo, COURIER,
                        new DeliverRequest("DELIVERED", "SELF", null)));
        assertEquals(409, duplicate.getCode());
        ApiException afterDelivery = assertThrows(ApiException.class,
                () -> delivered.world().orderService.sign(orderNo, "13700000002"));
        assertEquals(409, afterDelivery.getCode());

        Delivering failed = deliveringWorld("13700000003", "13700000004");
        String failedNo = failed.orderNos().get(0);
        failed.world().postmanOrders.deliver(failedNo, COURIER,
                new DeliverRequest("FAILED", null, "UNREACHABLE"));
        assertEquals(OrderState.SENDING, failed.world().orderState(failedNo));
        assertEquals(RouteStatus.ARRIVED, failed.world().routeStatus(failedNo, 5));
        assertTrue(orderNos(failed.world().adminPostmen.tasks(COURIER, "pending", null)).isEmpty());

        // 站点重新派给同一快递员后可以再次取件，订单不会卡死
        failed.world().adminOrders.assignCourier(failedNo, SHANGHAI, COURIER);
        assertTrue(orderNos(failed.world().adminPostmen.tasks(COURIER, "pending", null)).contains(failedNo));
        failed.world().postmanOrders.pickup(failedNo, COURIER, null);
        assertEquals(OrderState.DELIVERING, failed.world().orderState(failedNo));
        failed.world().orderService.sign(failedNo, "13700000004");
        assertEquals(OrderState.RECEIVED, failed.world().orderState(failedNo));
    }

    @Test
    void estimatedDaysFollowsApprovedRoute()
    {
        FlowWorld world = FlowWorld.seed();
        String orderNo = world.orderService.create(form("13700000001", "13700000002", "教材")).orderNo();
        // 北京→上海最短 4 段
        assertEquals(4, world.orderService.detail(orderNo).estimatedDays());
        assertNotNull(world.orderService.detail(orderNo).estimatedDeliveryAt());

        // 管理员选一条 6 段的绕行路线，详情中的时效随之更新
        world.adminOrders.approve(orderNo, 1L, List.of(1L, 2L, 3L, 4L, 7L, 5L, 6L));
        var detail = world.orderService.detail(orderNo);
        assertEquals(6, detail.trajectory().size() - 1);
        assertEquals(6, detail.estimatedDays());
    }

    @Test
    void resubmitRequiresSenderPhoneAndKeepsReturnDirection()
    {
        FlowWorld world = FlowWorld.seed();
        String orderNo = world.orderService.create(form("13700000001", "13700000002", "教材")).orderNo();
        world.adminOrders.reject(orderNo, "信息不完整");

        ApiException wrongPhone = assertThrows(ApiException.class,
                () -> world.orderService.resubmit(orderNo, form("13700000009", "13700000002", "教材")));
        assertEquals(403, wrongPhone.getCode());

        world.orderService.resubmit(orderNo, form("13700000001", "13700000008", "教材修订"));
        assertEquals(OrderState.WAITING, world.orderState(orderNo));

        world.adminOrders.reject(orderNo, "再拒一次");
        world.markReturnOf(orderNo, "LD-ORIGIN");
        ApiException changedDirection = assertThrows(ApiException.class,
                () -> world.orderService.resubmit(orderNo, form("13700000001", "13700000008", "教材修订", 1L, 5L)));
        assertEquals(400, changedDirection.getCode());

        world.orderService.resubmit(orderNo, form("13700000001", "13700000008", "只改货物"));
        assertEquals(OrderState.WAITING, world.orderState(orderNo));
        assertEquals("只改货物", world.orderService.detail(orderNo).goodsDesc());
    }

    @Test
    void overlongFieldsReturn400()
    {
        FlowWorld world = FlowWorld.seed();
        ApiException longGoods = assertThrows(ApiException.class,
                () -> world.orderService.create(form("13700000001", "13700000002", "货".repeat(256))));
        assertEquals(400, longGoods.getCode());

        String orderNo = world.orderService.create(form("13700000001", "13700000002", "教材")).orderNo();
        world.adminOrders.approve(orderNo, 1L, null);
        world.receipts.confirmReceipt(orderNo, BEIJING, 2L, null,
                new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50")));
        ApiException longTrip = assertThrows(ApiException.class,
                () -> world.adminOrders.assignTrip(orderNo, BEIJING, "T".repeat(33)));
        assertEquals(400, longTrip.getCode());

        String waiting = world.orderService.create(form("13700000003", "13700000004", "另一件")).orderNo();
        ApiException longReason = assertThrows(ApiException.class,
                () -> world.adminOrders.reject(waiting, "因".repeat(256)));
        assertEquals(400, longReason.getCode());
    }

    @Test
    void createQuotesByShortestPathAndReceiptPricingIsFinal()
    {
        FlowWorld world = FlowWorld.seed();
        var created = world.orderService.create(new OrderFormRequest(
                "张三", "13700000001", "李四", "13700000002",
                1L, 8L, "书籍", new BigDecimal("2.4"), new BigDecimal("12000")));
        assertEquals(0, new BigDecimal("37").compareTo(created.quotedPrice()));
        assertEquals(Boolean.FALSE, created.priceConfirmed());

        var onlyWeight = world.orderService.create(new OrderFormRequest(
                "张三", "13700000001", "李四", "13700000002",
                6L, 8L, "小件", new BigDecimal("1"), null));
        assertEquals(null, onlyWeight.quotedPrice());

        String orderNo = world.orderService.create(form("13700000001", "13700000002", "教材")).orderNo();
        world.adminOrders.approve(orderNo, 1L, null);
        world.receipts.confirmReceipt(orderNo, BEIJING, 2L, null,
                new ConfirmPriceRequest(new BigDecimal("1000"), new BigDecimal("1")));
        for (var hop : java.util.List.of(
                java.util.Map.entry(1L, "T1"),
                java.util.Map.entry(2L, "T2"),
                java.util.Map.entry(3L, "T3"),
                java.util.Map.entry(5L, "T4")))
        {
            world.adminOrders.assignTrip(orderNo, hop.getKey(), hop.getValue());
            world.adminOrders.depart(orderNo, hop.getKey());
            long next = hop.getKey() == 1L ? 2L : hop.getKey() == 2L ? 3L : hop.getKey() == 3L ? 5L : 6L;
            world.adminOrders.arrive(orderNo, next);
        }
        world.adminOrders.assignCourier(orderNo, 6L, COURIER);
        world.postmanOrders.pickup(orderNo, COURIER, null);
        assertEquals(OrderState.DELIVERING, world.orderState(orderNo));
        assertEquals(Boolean.TRUE, world.orderService.detail(orderNo).priceConfirmed());
        // 北京到上海 1377km，1kg 小体积，首重 23 元
        assertEquals(0, new BigDecimal("23").compareTo(world.orderService.detail(orderNo).price()));
    }

    private record Delivering(FlowWorld world, List<String> orderNos)
    {
    }

    private static Delivering deliveringWorld(String senderPhone, String receiverPhone)
    {
        FlowWorld world = FlowWorld.seed();
        String orderNo = world.orderService.create(form(senderPhone, receiverPhone, "教材")).orderNo();
        world.adminOrders.approve(orderNo, 1L, null);
        world.receipts.confirmReceipt(orderNo, BEIJING, 2L, null,
                new ConfirmPriceRequest(new BigDecimal("12000"), new BigDecimal("1.50")));
        for (var hop : java.util.List.of(
                java.util.Map.entry(1L, "T1"),
                java.util.Map.entry(2L, "T2"),
                java.util.Map.entry(3L, "T3"),
                java.util.Map.entry(5L, "T4")))
        {
            world.adminOrders.assignTrip(orderNo, hop.getKey(), hop.getValue());
            world.adminOrders.depart(orderNo, hop.getKey());
            long next = hop.getKey() == 1L ? 2L : hop.getKey() == 2L ? 3L : hop.getKey() == 3L ? 5L : 6L;
            world.adminOrders.arrive(orderNo, next);
        }
        world.adminOrders.assignCourier(orderNo, 6L, COURIER);
        world.postmanOrders.pickup(orderNo, COURIER, null);
        return new Delivering(world, List.of(orderNo));
    }

    private static List<String> orderNos(List<PostmanTask> tasks)
    {
        return tasks.stream().map(PostmanTask::getOrderNo).toList();
    }

    private static OrderFormRequest form(String senderPhone, String receiverPhone, String goods)
    {
        return form(senderPhone, receiverPhone, goods, 1L, 6L);
    }

    private static OrderFormRequest form(String senderPhone, String receiverPhone, String goods,
                                         long fromId, long toId)
    {
        return new OrderFormRequest("张三", senderPhone, "李四", receiverPhone, fromId, toId, goods,
                new BigDecimal("1.50"), null);
    }
}
