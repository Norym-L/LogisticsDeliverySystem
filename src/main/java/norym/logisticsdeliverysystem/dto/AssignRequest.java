package norym.logisticsdeliverysystem.dto;

/**
 * 节点管理员为当前路段指派承运人与车次。
 *
 * @param operatorId 节点管理员 id（Session 上线后改从会话取）
 * @param postmanId  承运邮递员 id
 * @param tripNo     车次号
 */
public record AssignRequest(Long operatorId, Long postmanId, String tripNo)
{
}
