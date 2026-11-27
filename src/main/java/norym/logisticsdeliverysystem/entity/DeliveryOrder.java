package norym.logisticsdeliverysystem.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送订单，对应 delivery_order 表。
 * <p>
 * 用可变 POJO 而非 record：这类实体有一半字段由服务器在不同阶段写入
 * （批准只写 3 个字段、签收只写 2 个），不可变对象会逼着每次都构造整行，
 * 既容易漏字段，也没法做"只更新指定列"的部分更新。
 */
public class DeliveryOrder
{
    private Long id;
    private String orderNo;

    private String senderName;
    private String senderPhone;
    private String receiverName;
    private String receiverPhone;

    private Long fromStationId;
    private Long toStationId;
    private String goodsDesc;
    private BigDecimal weight;
    private BigDecimal volumeCm3;
    private BigDecimal quotedPrice;
    private BigDecimal actualVolumeCm3;
    private BigDecimal actualWeight;
    private BigDecimal price;
    private LocalDateTime priceConfirmedAt;
    private Long priceConfirmedBy;

    private OrderState state;
    private Integer currentSeq;
    private Long currentStationId;

    private LocalDateTime approveDeadline;
    private Long approveBy;
    private LocalDateTime approveTime;
    private String rejectReason;

    private String refuseReason;
    private LocalDateTime refuseTime;
    private LocalDateTime receiveTime;

    private Integer forwardStationCount;
    private Integer resubmitCount;
    private Integer version;

    /** 退回单记录的原单号；普通订单为 null */
    private String returnOf;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getSenderName()
    {
        return senderName;
    }

    public void setSenderName(String senderName)
    {
        this.senderName = senderName;
    }

    public String getSenderPhone()
    {
        return senderPhone;
    }

    public void setSenderPhone(String senderPhone)
    {
        this.senderPhone = senderPhone;
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

    public Long getFromStationId()
    {
        return fromStationId;
    }

    public void setFromStationId(Long fromStationId)
    {
        this.fromStationId = fromStationId;
    }

    public Long getToStationId()
    {
        return toStationId;
    }

    public void setToStationId(Long toStationId)
    {
        this.toStationId = toStationId;
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

    public BigDecimal getQuotedPrice()
    {
        return quotedPrice;
    }

    public void setQuotedPrice(BigDecimal quotedPrice)
    {
        this.quotedPrice = quotedPrice;
    }

    public BigDecimal getActualVolumeCm3()
    {
        return actualVolumeCm3;
    }

    public void setActualVolumeCm3(BigDecimal actualVolumeCm3)
    {
        this.actualVolumeCm3 = actualVolumeCm3;
    }

    public BigDecimal getActualWeight()
    {
        return actualWeight;
    }

    public void setActualWeight(BigDecimal actualWeight)
    {
        this.actualWeight = actualWeight;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public LocalDateTime getPriceConfirmedAt()
    {
        return priceConfirmedAt;
    }

    public void setPriceConfirmedAt(LocalDateTime priceConfirmedAt)
    {
        this.priceConfirmedAt = priceConfirmedAt;
    }

    public Long getPriceConfirmedBy()
    {
        return priceConfirmedBy;
    }

    public void setPriceConfirmedBy(Long priceConfirmedBy)
    {
        this.priceConfirmedBy = priceConfirmedBy;
    }

    public boolean isPriceConfirmed()
    {
        return priceConfirmedAt != null;
    }

    public OrderState getState()
    {
        return state;
    }

    public void setState(OrderState state)
    {
        this.state = state;
    }

    public Integer getCurrentSeq()
    {
        return currentSeq;
    }

    public void setCurrentSeq(Integer currentSeq)
    {
        this.currentSeq = currentSeq;
    }

    public Long getCurrentStationId()
    {
        return currentStationId;
    }

    public void setCurrentStationId(Long currentStationId)
    {
        this.currentStationId = currentStationId;
    }

    public LocalDateTime getApproveDeadline()
    {
        return approveDeadline;
    }

    public void setApproveDeadline(LocalDateTime approveDeadline)
    {
        this.approveDeadline = approveDeadline;
    }

    public Long getApproveBy()
    {
        return approveBy;
    }

    public void setApproveBy(Long approveBy)
    {
        this.approveBy = approveBy;
    }

    public LocalDateTime getApproveTime()
    {
        return approveTime;
    }

    public void setApproveTime(LocalDateTime approveTime)
    {
        this.approveTime = approveTime;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public String getRefuseReason()
    {
        return refuseReason;
    }

    public void setRefuseReason(String refuseReason)
    {
        this.refuseReason = refuseReason;
    }

    public LocalDateTime getRefuseTime()
    {
        return refuseTime;
    }

    public void setRefuseTime(LocalDateTime refuseTime)
    {
        this.refuseTime = refuseTime;
    }

    public LocalDateTime getReceiveTime()
    {
        return receiveTime;
    }

    public void setReceiveTime(LocalDateTime receiveTime)
    {
        this.receiveTime = receiveTime;
    }

    public Integer getForwardStationCount()
    {
        return forwardStationCount;
    }

    public void setForwardStationCount(Integer forwardStationCount)
    {
        this.forwardStationCount = forwardStationCount;
    }

    public Integer getResubmitCount()
    {
        return resubmitCount;
    }

    public void setResubmitCount(Integer resubmitCount)
    {
        this.resubmitCount = resubmitCount;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public String getReturnOf()
    {
        return returnOf;
    }

    public void setReturnOf(String returnOf)
    {
        this.returnOf = returnOf;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime)
    {
        this.updateTime = updateTime;
    }
}
