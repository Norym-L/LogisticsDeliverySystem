package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;

/**
 * 驿站视图。address 来自 station.address，用户端网点查询页直接展示。
 * latitude / longitude 为 WGS84 坐标，供 Leaflet + OpenStreetMap 轨迹地图使用。
 */
public record StationView(Long id, String code, String name, String address,
                          BigDecimal latitude, BigDecimal longitude)
{
}
