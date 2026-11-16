package norym.logisticsdeliverysystem.dto;

/**
 * 为中转段指派车次。中转运输由车次承运，不涉及快递员。
 */
public record AssignTripRequest(String tripNo)
{
}
