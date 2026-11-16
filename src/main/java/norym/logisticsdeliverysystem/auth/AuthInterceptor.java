package norym.logisticsdeliverysystem.auth;

import norym.logisticsdeliverysystem.exception.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 只拦 /api/admin/** 与 /api/postman/**，用户端与登录接口保持公开。
 * <p>
 * 抛出的 ApiException 会被 @RestControllerAdvice 接住，返回统一的 401/403 JSON。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor
{
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        String path = request.getRequestURI();
        boolean adminPath = path.startsWith("/api/admin/");
        boolean postmanPath = path.startsWith("/api/postman/");
        if (!adminPath && !postmanPath)
        {
            return true;
        }

        HttpSession session = request.getSession(false);
        Object attribute = session == null ? null : session.getAttribute(AuthService.SESSION_KEY);
        if (!(attribute instanceof SessionUser user))
        {
            throw new ApiException(401, "请先登录");
        }

        if (postmanPath && user.role() != Role.POSTMAN)
        {
            throw new ApiException(403, "该接口仅限配送员访问");
        }
        if (adminPath && user.role() == Role.POSTMAN)
        {
            throw new ApiException(403, "该接口仅限管理员访问");
        }
        return true;
    }
}
