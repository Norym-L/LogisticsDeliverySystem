package norym.logisticsdeliverysystem.dto;

import norym.logisticsdeliverysystem.auth.SessionUser;

/**
 * 登录成功后返回的身份信息，不含任何密码字段。
 */
public record LoginView(Long id, String username, String name, String role, Long stationId)
{
    public static LoginView of(SessionUser user)
    {
        return new LoginView(user.id(), user.username(), user.name(), user.role().name(), user.stationId());
    }
}
