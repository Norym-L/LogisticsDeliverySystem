package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.auth.AuthService;
import norym.logisticsdeliverysystem.auth.SessionUser;
import norym.logisticsdeliverysystem.dto.ApiResult;
import norym.logisticsdeliverysystem.dto.LoginRequest;
import norym.logisticsdeliverysystem.dto.LoginView;
import norym.logisticsdeliverysystem.exception.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import jakarta.servlet.http.HttpSession;

/**
 * 登录入口。管理员与配送员分两个接口，因为凭据分属两张表。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController
{
    private final AuthService authService;

    public AuthController(AuthService authService)
    {
        this.authService = authService;
    }

    @PostMapping("/operator/login")
    public ApiResult<LoginView> operatorLogin(@RequestBody LoginRequest request, HttpSession session)
    {
        return ApiResult.ok(LoginView.of(
                authService.loginOperator(request == null ? null : request.username(),
                        request == null ? null : request.password(), session)));
    }

    @PostMapping("/postman/login")
    public ApiResult<LoginView> postmanLogin(@RequestBody LoginRequest request, HttpSession session)
    {
        return ApiResult.ok(LoginView.of(
                authService.loginPostman(request == null ? null : request.username(),
                        request == null ? null : request.password(), session)));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpSession session)
    {
        authService.logout(session);
        return ApiResult.ok();
    }

    /** 前端用来判断当前登录态 */
    @GetMapping("/me")
    public ApiResult<LoginView> me(@SessionAttribute(name = AuthService.SESSION_KEY, required = false) SessionUser user)
    {
        if (user == null)
        {
            throw new ApiException(401, "未登录");
        }
        return ApiResult.ok(LoginView.of(user));
    }
}
