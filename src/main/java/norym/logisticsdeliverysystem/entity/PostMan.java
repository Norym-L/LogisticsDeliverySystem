package norym.logisticsdeliverysystem.entity;

/**
 * 邮递员信息
 * @param id            id
 * @param name          姓名
 * @param phoneNumber   电话号码
 */
public record PostMan(Integer id, String name, String phoneNumber)
{
}
