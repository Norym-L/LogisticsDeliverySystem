package norym.logisticsdeliverysystem.auth;

import java.io.Serializable;

/**
 * 登录后放进 HttpSession 的用户信息。
 * <p>
 * 只放展会上要用的字段，不放密码摘要——session 里的东西虽然不直接暴露给客户端，
 * 但服务端序列化、日志打印都可能把它带出去。
 *
 * @param stationId 所属驿站：节点管理员与配送员有值，路径管理员为 null
 */
public record SessionUser(Long id, String username, String name, Role role, Long stationId)
        implements Serializable
{
}
