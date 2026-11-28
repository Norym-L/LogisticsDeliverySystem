package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 路线上的一个节点，对应"邮件路径"里的一行。
 *
 * @param seq         序号，本单去程从 1 开始（拒收由独立退回单承载，不追加回程行）
 * @param stationId   驿站主键，前端用来和当前位置对应
 * @param stationName 驿站名称
 * @param status      路段状态：PENDING / ARRIVED / DELIVERING / SIGNED / REFUSED
 * @param departTime  从本站发出的时间，尚未发运为 null
 * @param arriveTime  实际到达时间，未到达为 null
 * @param tripNo      该段承运车次
 * @param latitude    驿站 WGS84 纬度，来自 station.latitude
 * @param longitude   驿站 WGS84 经度，来自 station.longitude
 */
public record RouteNodeView(
        int seq,
        Long stationId,
        String stationName,
        String status,
        LocalDateTime departTime,
        LocalDateTime arriveTime,
        String tripNo,
        BigDecimal latitude,
        BigDecimal longitude)
{
}
