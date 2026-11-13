package norym.logisticsdeliverysystem.entity;

import java.sql.Date;

public class Order
{
    private final Integer id;                     // 订单号
    private final String posStart;                // 发货地点
    private final String posEnd;                  // 收货地点
    private String posNow;                        // 当前地点
    private final String sendUserName;            // 发货人姓名
    private final String sendUserPhoneNumber;     // 发货人手机号
    private final String receiveUserName;         // 收货人姓名
    private final String receiveUserPhoneNumber;  // 收货人手机号
    private final Date sendDate;                  // 发货日期
    private OrderState orderState;                // 状态

    public Order(Integer id, String posStart, String posEnd, String posNow, String sendUserName, String sendUserPhoneNumber, String receiveUserName, String receiveUserPhoneNumber, Date sendDate, OrderState orderState)
    {
        this.id = id;
        this.posStart = posStart;
        this.posEnd = posEnd;
        this.posNow = posNow;
        this.sendUserName = sendUserName;
        this.sendUserPhoneNumber = sendUserPhoneNumber;
        this.receiveUserName = receiveUserName;
        this.receiveUserPhoneNumber = receiveUserPhoneNumber;
        this.sendDate = sendDate;
        this.orderState = orderState;
    }

    @Override
    public String toString()
    {
        return "Order{" + "id=" + id + ", posStart='" + posStart + '\'' + ", posEnd='" + posEnd + '\'' + ", posNow='" + posNow + '\'' + ", sendUserName='" + sendUserName + '\'' + ", sendUserPhoneNumber='" + sendUserPhoneNumber + '\'' + ", receiveUserName='" + receiveUserName + '\'' + ", receiveUserPhoneNumber='" + receiveUserPhoneNumber + '\'' + ", sendDate=" + sendDate + ", orderState=" + orderState + '}';
    }

    public void setPosNow(String posNow)
    {
        this.posNow = posNow;
    }

    public void setState(OrderState orderState)
    {
        this.orderState = orderState;
    }

    public Integer getId()
    {
        return id;
    }

    public String getPosStart()
    {
        return posStart;
    }

    public String getPosEnd()
    {
        return posEnd;
    }

    public String getPosNow()
    {
        return posNow;
    }

    public String getSendUserName()
    {
        return sendUserName;
    }

    public String getSendUserPhoneNumber()
    {
        return sendUserPhoneNumber;
    }

    public String getReceiveUserName()
    {
        return receiveUserName;
    }

    public String getReceiveUserPhoneNumber()
    {
        return receiveUserPhoneNumber;
    }

    public Date getSendDate()
    {
        return sendDate;
    }

    public OrderState getState()
    {
        return orderState;
    }
}
