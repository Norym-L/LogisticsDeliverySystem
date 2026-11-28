package norym.logisticsdeliverysystem.dto;

/**
 * 驿站视图。address 来自 station.address，用户端网点查询页直接展示。
 */
public record StationView(Long id, String code, String name, String address)
{
}
