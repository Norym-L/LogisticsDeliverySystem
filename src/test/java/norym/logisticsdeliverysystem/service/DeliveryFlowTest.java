package norym.logisticsdeliverysystem.service;

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
    private static final long COURIER = 11L;

    @Test
    void mainFlowRejectsSkippedArrivalAndListsCourierTasksByOrderState()
    {
        FlowWorld world = FlowWorld.seed();
        String orderNo = world.orderService.create(form("13700000001", "13700000002", "教材")).orderNo();

        world.adminOrders.approve(orderNo, 1L, null);
        var detail = world.orderService.detail(orderNo);
        assertEquals(List.of("北京转运中心", "天津转运中心", "济南转运中心", "南京转运中心", "上海转运中心"),
                detail.trajectory().stream().map(n -> n.stationName()).toList());
        assertEquals(RouteStatus.ARRIVED.name(), detail.trajectory().get(0).status());
        assertEquals(RouteStatus.PENDING.name(), detail.trajectory().get(1).status());

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

        world.postmanOrders.pickup(orderNo, COURIER);
        assertEquals(OrderState.DELIVERING, world.orderState(orderNo));
        assertEquals(RouteStatus.DELIVERING, world.routeStatus(orderNo, 5));
        assertTrue(orderNos(world.adminPostmen.tasks(COURIER, "pending", null)).contains(orderNo));

        world.orderService.sign(orderNo, "13700000002");
        assertEquals(OrderState.RECEIVED, world.orderState(orderNo));
        assertEquals(RouteStatus.SIGNED, world.routeStatus(orderNo, 5));
        assertTrue(orderNos(world.adminPostmen.tasks(COURIER, "pending", null)).isEmpty());
        assertTrue(orderNos(world.adminPostmen.tasks(COURIER, "completed", null)).contains(orderNo));
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
        ApiException longTrip = assertThrows(ApiException.class,
                () -> world.adminOrders.assignTrip(orderNo, BEIJING, "T".repeat(33)));
        assertEquals(400, longTrip.getCode());

        String waiting = world.orderService.create(form("13700000003", "13700000004", "另一件")).orderNo();
        ApiException longReason = assertThrows(ApiException.class,
                () -> world.adminOrders.reject(waiting, "因".repeat(256)));
        assertEquals(400, longReason.getCode());
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
        return new OrderFormRequest("张三", senderPhone, "李四", receiverPhone, fromId, toId, goods, new BigDecimal("1.50"));
    }
}
