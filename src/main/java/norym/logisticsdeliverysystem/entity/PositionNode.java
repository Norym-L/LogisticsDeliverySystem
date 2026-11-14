package norym.logisticsdeliverysystem.entity;

import java.sql.Date;

/**
 * 驿站信息
 * @param id        编号
 * @param name      名称
 * @param address   地址
 * @param date      到达日期
 * @param info      详细信息
 */
public record PositionNode(Integer id, String name, String address, Date date, String info)
{
}
