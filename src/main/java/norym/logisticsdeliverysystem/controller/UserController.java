package norym.logisticsdeliverysystem.controller;

import norym.logisticsdeliverysystem.service.UserService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController
{
    private final UserService userService;

    public UserController(UserService userService)
    {
        this.userService = userService;
    }

    @PostMapping("/getOrderIdsByPhoneNumber/{phone_number}")
    public String getOrderIdsByPhoneNumber(@PathVariable String phone_number)
    {
        // TODO
        return "电话号码为： " + phone_number;
    }

    @PostMapping("/getDetailedInfoByOrderId/{order_id}")
    public String getDetailedInfoByOrderId(@PathVariable String order_id)
    {
        // TODO
        return userService.testGetOrder();

//        return "订单ID为： " + order_id;
    }

    @PostMapping("/testHello")
    public String testHello()
    {
        return "hello";
    }
}
