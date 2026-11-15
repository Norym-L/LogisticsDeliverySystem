package norym.logisticsdeliverysystem.mapper;

import norym.logisticsdeliverysystem.entity.DeliveryOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeliveryOrderMapper
{
    int insert(DeliveryOrder order);

    DeliveryOrder findByOrderNo(@Param("orderNo") String orderNo);

    /** 手机号同时匹配寄件人与收件人，否则收件人查不到发给自己的件 */
    List<DeliveryOrder> findByPhone(@Param("phone") String phone);

    /**
     * 签收：手机号、订单状态三个条件一起卡在 WHERE 里。
     * 返回 0 表示校验未通过或已被并发修改。
     */
    int sign(@Param("orderNo") String orderNo,
             @Param("receiverPhone") String receiverPhone,
             @Param("now") LocalDateTime now);

    int refuse(@Param("orderNo") String orderNo,
               @Param("receiverPhone") String receiverPhone,
               @Param("reason") String reason,
               @Param("now") LocalDateTime now);

    /**
     * 被拒后修改重提：只允许 REJECTED 且未超过重提上限。
     */
    int resubmit(@Param("order") DeliveryOrder order,
                 @Param("deadline") LocalDateTime deadline,
                 @Param("maxResubmit") int maxResubmit);

    /** 审批超时批量转 REJECTED，返回受影响行数 */
    int rejectTimeout(@Param("now") LocalDateTime now);
}
