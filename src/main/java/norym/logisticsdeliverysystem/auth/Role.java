package norym.logisticsdeliverysystem.auth;

/**
 * 登录角色。operator 表只存前两种，postman 表存第三种。
 */
public enum Role
{
    PATH_ADMIN,     // 路径管理员：审批、选路线
    STATION_ADMIN,  // 节点管理员：为本站货物指派运力
    POSTMAN         // 配送员：扫码发运与到站
}
