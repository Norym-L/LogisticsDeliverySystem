package norym.logisticsdeliverysystem.entity;

import java.math.BigDecimal;

/**
 * 驿站间线路，对应 route_edge 表。
 * <p>
 * 存的是无向图的一条边，约定 stationAId &lt; stationBId，
 * 否则 (1,2) 和 (2,1) 会同时存在，最短路算法读到重复边就会算错。
 */
public class RouteEdge
{
    private Long id;
    private Long stationAId;
    private Long stationBId;
    private BigDecimal distanceKm;
    private Integer status;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getStationAId()
    {
        return stationAId;
    }

    public void setStationAId(Long stationAId)
    {
        this.stationAId = stationAId;
    }

    public Long getStationBId()
    {
        return stationBId;
    }

    public void setStationBId(Long stationBId)
    {
        this.stationBId = stationBId;
    }

    public BigDecimal getDistanceKm()
    {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm)
    {
        this.distanceKm = distanceKm;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }
}
