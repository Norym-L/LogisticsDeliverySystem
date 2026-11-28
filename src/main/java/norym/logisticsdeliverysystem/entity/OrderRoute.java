package norym.logisticsdeliverysystem.entity;

import java.time.LocalDateTime;

/**
 * 订单路线，对应 order_route 表。
 * <p>
 * seq 从 1 开始：1 是始发站，n 是目的站。批准后整条路线固化，
 * 但始发站那一段要等始发站确认收件后才置为 ARRIVED。
 * 拒收不追加回程行，而是生成一张方向相反的独立退回单；
 * 本单 current_seq 单调递增，"取下一节点"永远是 seq = current_seq + 1。
 */
public class OrderRoute
{
    private Long id;
    private Long orderId;
    private Integer seq;
    private Long stationId;
    private String arriveTripNo;
    private LocalDateTime plannedTime;
    private LocalDateTime departTime;
    private LocalDateTime actualArriveTime;
    private Long arriveOperatorId;
    private Long postmanId;
    private RouteStatus status;
    private String remark;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getOrderId()
    {
        return orderId;
    }

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public Integer getSeq()
    {
        return seq;
    }

    public void setSeq(Integer seq)
    {
        this.seq = seq;
    }

    public Long getStationId()
    {
        return stationId;
    }

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    public String getArriveTripNo()
    {
        return arriveTripNo;
    }

    public void setArriveTripNo(String arriveTripNo)
    {
        this.arriveTripNo = arriveTripNo;
    }

    public LocalDateTime getPlannedTime()
    {
        return plannedTime;
    }

    public void setPlannedTime(LocalDateTime plannedTime)
    {
        this.plannedTime = plannedTime;
    }

    public LocalDateTime getDepartTime()
    {
        return departTime;
    }

    public void setDepartTime(LocalDateTime departTime)
    {
        this.departTime = departTime;
    }

    public LocalDateTime getActualArriveTime()
    {
        return actualArriveTime;
    }

    public void setActualArriveTime(LocalDateTime actualArriveTime)
    {
        this.actualArriveTime = actualArriveTime;
    }

    public Long getArriveOperatorId()
    {
        return arriveOperatorId;
    }

    public void setArriveOperatorId(Long arriveOperatorId)
    {
        this.arriveOperatorId = arriveOperatorId;
    }

    public Long getPostmanId()
    {
        return postmanId;
    }

    public void setPostmanId(Long postmanId)
    {
        this.postmanId = postmanId;
    }

    public RouteStatus getStatus()
    {
        return status;
    }

    public void setStatus(RouteStatus status)
    {
        this.status = status;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
