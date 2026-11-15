package norym.logisticsdeliverysystem.dto;

import java.time.LocalDateTime;

/**
 * 路线上的一个节点，对应"邮件路径"里的一行。
 *
 * @param seq        序号，去程从 1 开始，回程接在去程之后
 * @param stationName 驿站名称
 * @param status     路段状态：PENDING / ARRIVED / DELIVERING / SIGNED / REFUSED
 * @param arriveTime 实际到达时间，未到达为 null
 * @param tripNo     抵达本站所乘车次，始发站为 null
 * @param back       是否属于回程段（seq 大于去程站数）
 */
public record RouteNodeView(
        int seq,
        String stationName,
        String status,
        LocalDateTime arriveTime,
        String tripNo,
        boolean back)
{
}
