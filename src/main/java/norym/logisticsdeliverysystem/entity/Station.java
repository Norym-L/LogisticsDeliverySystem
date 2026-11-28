package norym.logisticsdeliverysystem.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 驿站，对应 station 表。
 * <p>
 * latitude / longitude 为 WGS84 坐标，供轨迹地图使用；Leaflet + OpenStreetMap
 * 底图同为 WGS84，不需要 GCJ-02 / BD-09 偏移转换。坐标只用于地图展示，
 * 不改变站名和 address，也不代表门牌级定位。
 */
public class Station
{
    private Long id;
    private String code;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer status;
    private LocalDateTime createTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public BigDecimal getLatitude()
    {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude)
    {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude()
    {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude)
    {
        this.longitude = longitude;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }
}
