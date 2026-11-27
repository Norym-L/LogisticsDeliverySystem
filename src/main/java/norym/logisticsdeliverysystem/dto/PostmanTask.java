package norym.logisticsdeliverysystem.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送员任务列表项：一眼要看清"从哪到哪、哪趟车、货是谁的"。
 * <p>
 * 用 POJO 而不是 record：这是 MyBatis 的查询结果映射对象，需要 setter。
 * 响应里的 lastMile 由 {@link #isLastMile()} 计算得出，库里不存这个字段。
 */
public class PostmanTask
{
    private String orderNo;
    private String orderState;
    private Integer seq;
    private Integer forwardStationCount;
    private String fromStationName;
    private String toStationName;
    private String tripNo;
    private LocalDateTime departTime;
    private LocalDateTime arriveTime;
    private String legStatus;
    private String senderName;
    private String receiverName;
    private String receiverPhone;
    private String goodsDesc;
    private BigDecimal weight;
    private BigDecimal volumeCm3;
    private BigDecimal actualWeight;
    private BigDecimal actualVolumeCm3;
    private BigDecimal quotedPrice;
    private BigDecimal price;
    private Boolean priceConfirmed;

    /** 该段是否属于最后一公里：目的站那一段 */
    public boolean isLastMile()
    {
        return seq != null && forwardStationCount != null && seq.equals(forwardStationCount);
    }

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getOrderState()
    {
        return orderState;
    }

    public void setOrderState(String orderState)
    {
        this.orderState = orderState;
    }

    public Integer getSeq()
    {
        return seq;
    }

    public void setSeq(Integer seq)
    {
        this.seq = seq;
    }

    public Integer getForwardStationCount()
    {
        return forwardStationCount;
    }

    public void setForwardStationCount(Integer forwardStationCount)
    {
        this.forwardStationCount = forwardStationCount;
    }

    public String getFromStationName()
    {
        return fromStationName;
    }

    public void setFromStationName(String fromStationName)
    {
        this.fromStationName = fromStationName;
    }

    public String getToStationName()
    {
        return toStationName;
    }

    public void setToStationName(String toStationName)
    {
        this.toStationName = toStationName;
    }

    public String getTripNo()
    {
        return tripNo;
    }

    public void setTripNo(String tripNo)
    {
        this.tripNo = tripNo;
    }

    public LocalDateTime getDepartTime()
    {
        return departTime;
    }

    public void setDepartTime(LocalDateTime departTime)
    {
        this.departTime = departTime;
    }

    public LocalDateTime getArriveTime()
    {
        return arriveTime;
    }

    public void setArriveTime(LocalDateTime arriveTime)
    {
        this.arriveTime = arriveTime;
    }

    public String getLegStatus()
    {
        return legStatus;
    }

    public void setLegStatus(String legStatus)
    {
        this.legStatus = legStatus;
    }

    public String getSenderName()
    {
        return senderName;
    }

    public void setSenderName(String senderName)
    {
        this.senderName = senderName;
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

    public String getGoodsDesc()
    {
        return goodsDesc;
    }

    public void setGoodsDesc(String goodsDesc)
    {
        this.goodsDesc = goodsDesc;
    }

    public BigDecimal getWeight()
    {
        return weight;
    }

    public void setWeight(BigDecimal weight)
    {
        this.weight = weight;
    }

    public BigDecimal getVolumeCm3()
    {
        return volumeCm3;
    }

    public void setVolumeCm3(BigDecimal volumeCm3)
    {
        this.volumeCm3 = volumeCm3;
    }

    public BigDecimal getActualWeight()
    {
        return actualWeight;
    }

    public void setActualWeight(BigDecimal actualWeight)
    {
        this.actualWeight = actualWeight;
    }

    public BigDecimal getActualVolumeCm3()
    {
        return actualVolumeCm3;
    }

    public void setActualVolumeCm3(BigDecimal actualVolumeCm3)
    {
        this.actualVolumeCm3 = actualVolumeCm3;
    }

    public BigDecimal getQuotedPrice()
    {
        return quotedPrice;
    }

    public void setQuotedPrice(BigDecimal quotedPrice)
    {
        this.quotedPrice = quotedPrice;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public Boolean getPriceConfirmed()
    {
        return priceConfirmed;
    }

    public void setPriceConfirmed(Boolean priceConfirmed)
    {
        this.priceConfirmed = priceConfirmed;
    }
}
