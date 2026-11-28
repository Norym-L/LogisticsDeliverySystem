package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;

/**
 * 包裹在地图上的当前位置。只做站点/路段级示意，不接车辆实时 GPS。
 * <p>
 * mode 取值：
 * <ul>
 *   <li>NOT_SHIPPED：尚未发运（待审批、已拒绝、已取消），位置在始发站；</li>
 *   <li>AT_STATION：在某一站（始发站待收件/待发运，运输途中到站待发）；</li>
 *   <li>IN_TRANSIT：已从 fromStation 发运、下一站尚未到达，坐标取两站中点；</li>
 *   <li>DELIVERING：在目的站做最后一公里派送；</li>
 *   <li>RECEIVED / REFUSED：已签收 / 已拒收，停在最后已知站。</li>
 * </ul>
 *
 * @param mode            位置模式，见类注释
 * @param label           给用户看的中文说明
 * @param seq             当前位置对应的 order_route.seq；无路线时为 0
 * @param stationId       在站时是所在站；在途时是刚发运的站；无路线时可能为始发站
 * @param stationName     同上
 * @param latitude        地图标记的 WGS84 纬度；在途为两站中点
 * @param longitude       地图标记的 WGS84 经度；在途为两站中点
 * @param fromStationId   在途路段的出发站，其它模式为 null
 * @param fromStationName 在途路段的出发站名
 * @param toStationId     在途路段的下一站，其它模式为 null
 * @param toStationName   在途路段的下一站名
 */
public record TrackPositionView(
        String mode,
        String label,
        int seq,
        Long stationId,
        String stationName,
        BigDecimal latitude,
        BigDecimal longitude,
        Long fromStationId,
        String fromStationName,
        Long toStationId,
        String toStationName)
{
}
