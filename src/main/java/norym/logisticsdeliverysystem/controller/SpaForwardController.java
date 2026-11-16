package norym.logisticsdeliverysystem.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端使用 History 路由，刷新 /login、/postmen 时浏览器会向服务端要这些路径。
 * 这里把它们回落到 index.html，由 Vue Router 接管；/api 与 /assets 不受影响。
 */
@Controller
public class SpaForwardController
{
    @GetMapping({"/login", "/postmen"})
    public String forward()
    {
        return "forward:/index.html";
    }
}
