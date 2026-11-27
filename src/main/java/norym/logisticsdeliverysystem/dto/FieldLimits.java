package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;

/**
 * 与表字段长度对齐的上限。超出时接口返回 400，避免数据库截断变成 500。
 */
public final class FieldLimits
{
    public static final int NAME = 32;
    public static final int GOODS = 255;
    public static final int REASON = 255;
    public static final int TRIP_NO = 32;
    public static final BigDecimal MAX_WEIGHT = new BigDecimal("999999.99");

    private FieldLimits()
    {
    }
}
