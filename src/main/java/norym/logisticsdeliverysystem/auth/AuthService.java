package norym.logisticsdeliverysystem.auth;

import norym.logisticsdeliverysystem.entity.Operator;
import norym.logisticsdeliverysystem.entity.Postman;
import norym.logisticsdeliverysystem.exception.ApiException;
import norym.logisticsdeliverysystem.mapper.OperatorMapper;
import norym.logisticsdeliverysystem.mapper.PostmanMapper;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;

/**
 * 登录与登出。Session 里只放 {@link SessionUser}，不放密码相关字段。
 */
@Service
public class AuthService
{
    public static final String SESSION_KEY = "LOGIN_USER";

    private final OperatorMapper operatorMapper;
    private final PostmanMapper postmanMapper;

    public AuthService(OperatorMapper operatorMapper, PostmanMapper postmanMapper)
    {
        this.operatorMapper = operatorMapper;
        this.postmanMapper = postmanMapper;
    }

    public SessionUser loginOperator(String username, String password, HttpSession session)
    {
        requireCredentials(username, password);
        Operator operator = operatorMapper.findByUsername(username.trim());
        // 用户不存在和密码错误返回同一句话，避免被用来枚举账号
        if (operator == null || !PasswordHasher.matches(operator.getPasswordHash(), operator.getSalt(), password))
        {
            throw new ApiException(401, "用户名或密码错误");
        }
        if (operator.getStatus() != null && operator.getStatus() == 0)
        {
            throw new ApiException(403, "账号已停用");
        }

        Role role;
        try
        {
            role = Role.valueOf(operator.getRole());
        }
        catch (IllegalArgumentException e)
        {
            throw new ApiException(403, "账号角色异常");
        }

        SessionUser user = new SessionUser(operator.getId(), operator.getUsername(),
                operator.getRealName(), role, operator.getStationId());
        session.setAttribute(SESSION_KEY, user);
        return user;
    }

    public SessionUser loginPostman(String username, String password, HttpSession session)
    {
        requireCredentials(username, password);
        Postman postman = postmanMapper.findByUsername(username.trim());
        if (postman == null || !PasswordHasher.matches(postman.getPasswordHash(), postman.getSalt(), password))
        {
            throw new ApiException(401, "用户名或密码错误");
        }
        if (postman.getStatus() != null && postman.getStatus() == 0)
        {
            throw new ApiException(403, "账号已停用");
        }

        SessionUser user = new SessionUser(postman.getId(), postman.getUsername(),
                postman.getName(), Role.POSTMAN, postman.getStationId());
        session.setAttribute(SESSION_KEY, user);
        return user;
    }

    public void logout(HttpSession session)
    {
        if (session != null)
        {
            session.invalidate();
        }
    }

    private void requireCredentials(String username, String password)
    {
        if (username == null || username.isBlank() || password == null || password.isEmpty())
        {
            throw new ApiException(400, "用户名和密码不能为空");
        }
    }
}
