package norym.logisticsdeliverysystem.dto;

/**
 * 为最后一公里指派快递员。必须是目的站的快递员。
 */
public record AssignCourierRequest(Long postmanId)
{
}
