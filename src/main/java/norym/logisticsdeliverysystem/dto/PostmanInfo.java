package norym.logisticsdeliverysystem.dto;

/**
 * 管理端看到的配送员档案，附带"手里的单"统计。
 * <p>
 * activeTasks 是还没到站的段（PENDING），doneTasks 是已到站/已签收/已拒收的段。
 * 两者都在 SQL 里用条件聚合算好，不在 Java 里再查一遍。
 */
public class PostmanInfo
{
    private Long id;
    private String username;
    private String name;
    private String phone;
    private Long stationId;
    private String stationName;
    private Integer status;
    private Integer activeTasks;
    private Integer doneTasks;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public Long getStationId()
    {
        return stationId;
    }

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    public String getStationName()
    {
        return stationName;
    }

    public void setStationName(String stationName)
    {
        this.stationName = stationName;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Integer getActiveTasks()
    {
        return activeTasks;
    }

    public void setActiveTasks(Integer activeTasks)
    {
        this.activeTasks = activeTasks;
    }

    public Integer getDoneTasks()
    {
        return doneTasks;
    }

    public void setDoneTasks(Integer doneTasks)
    {
        this.doneTasks = doneTasks;
    }
}
