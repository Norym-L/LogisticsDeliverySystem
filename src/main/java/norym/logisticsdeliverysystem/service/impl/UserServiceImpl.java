package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.entity.Order;
import norym.logisticsdeliverysystem.entity.OrderState;
import norym.logisticsdeliverysystem.service.UserService;
import org.springframework.stereotype.Service;

import java.sql.Date;

@Service
public class UserServiceImpl implements UserService {
    @Override
    public String testGetOrder()
    {
        var order = new Order(1, "a", "b", "c", "norym", "12345678901", "arrym", "12345678902", Date.valueOf("2026-11-13"), OrderState.WAITING);
        return order.toString();
    }
}
