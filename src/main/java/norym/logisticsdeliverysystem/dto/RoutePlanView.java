package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 系统推荐的运输路线，供路径管理员批准前参考或调整。
 */
public record RoutePlanView(List<StationView> stations, BigDecimal totalKm)
{
}
