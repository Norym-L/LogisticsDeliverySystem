package norym.logisticsdeliverysystem.dto;

import java.time.LocalDateTime;

/**
 * 节点管理员本站待办的一行。
 * <p>
 * bucket：DEPART 待发运 / ARRIVE 待到货 / DISPATCH 待派快递员。
 * 用 POJO 是因为这是 MyBatis 的查询结果。
 */
public class StationInboxItem
{
    private String bucket;
    private String orderNo;
    private String state;
    private String fromStation;
    private String toStation;
    private String nextStation;
    private Integer currentSeq;
    private Integer forwardStationCount;
    private String tripNo;
    private String goodsDesc;
    private String receiverName;
    private String receiverPhone;
    private LocalDateTime createTime;

    public String getBucket()
    {
        return bucket;
    }

    public void setBucket(String bucket)
    {
        this.bucket = bucket;
    }

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getState()
    {
        return state;
    }

    public void setState(String state)
    {
        this.state = state;
    }

    public String getFromStation()
    {
        return fromStation;
    }

    public void setFromStation(String fromStation)
    {
        this.fromStation = fromStation;
    }

    public String getToStation()
    {
        return toStation;
    }

    public void setToStation(String toStation)
    {
        this.toStation = toStation;
    }

    public String getNextStation()
    {
        return nextStation;
    }

    public void setNextStation(String nextStation)
    {
        this.nextStation = nextStation;
    }

    public Integer getCurrentSeq()
    {
        return currentSeq;
    }

    public void setCurrentSeq(Integer currentSeq)
    {
        this.currentSeq = currentSeq;
    }

    public Integer getForwardStationCount()
    {
        return forwardStationCount;
    }

    public void setForwardStationCount(Integer forwardStationCount)
    {
        this.forwardStationCount = forwardStationCount;
    }

    public String getTripNo()
    {
        return tripNo;
    }

    public void setTripNo(String tripNo)
    {
        this.tripNo = tripNo;
    }

    public String getGoodsDesc()
    {
        return goodsDesc;
    }

    public void setGoodsDesc(String goodsDesc)
    {
        this.goodsDesc = goodsDesc;
    }

    public String getReceiverName()
    {
        return receiverName;
    }

    public void setReceiverName(String receiverName)
    {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone()
    {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone)
    {
        this.receiverPhone = receiverPhone;
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
