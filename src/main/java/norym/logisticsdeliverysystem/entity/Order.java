package norym.logisticsdeliverysystem.entity;

import java.sql.Date;
import java.util.LinkedList;

/**
 * 订单信息
 * @param id                     订单号
 * @param posStart               发货地点
 * @param posEnd                 收货地点
 * @param posList                途径地点
 * @param posNow                 当前位置
 * @param sendUserName           发货人姓名
 * @param sendUserPhoneNumber    发货人手机号
 * @param receiveUserName        收货人姓名
 * @param receiveUserPhoneNumber 收货人手机号
 * @param sendDate               发货日期
 * @param orderState             状态
 * @param postMan                邮递员信息（仅最后一站有）
 */
public record Order(Integer id, PositionNode posStart, PositionNode posEnd, LinkedList<PositionNode> posList, PositionNode posNow,
                    String sendUserName, String sendUserPhoneNumber, String receiveUserName,
                    String receiveUserPhoneNumber, Date sendDate, OrderState orderState, PostMan postMan)
{
}
