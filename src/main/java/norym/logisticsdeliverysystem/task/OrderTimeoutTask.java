package norym.logisticsdeliverysystem.task;

import norym.logisticsdeliverysystem.mapper.DeliveryOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审批超时扫描：把超过 approve_deadline 仍处于 WAITING 的订单转成 REJECTED。
 * <p>
 * 与路径管理员的批准接口并发时，靠 SQL 里的 state='WAITING' 条件收敛：
 * 谁先成功谁生效，另一次影响 0 行，不会出现"既批准又拒绝"。
 */
@Component
public class OrderTimeoutTask
{
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    private final DeliveryOrderMapper orderMapper;

    public OrderTimeoutTask(DeliveryOrderMapper orderMapper)
    {
        this.orderMapper = orderMapper;
    }

    @Scheduled(fixedDelayString = "${logistics.approve-scan-interval-ms:5000}")
    public void rejectExpired()
    {
        int rows = orderMapper.rejectTimeout(LocalDateTime.now());
        if (rows > 0)
        {
            log.info("审批超时自动拒绝 {} 单", rows);
        }
    }
}
