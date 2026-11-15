package norym.logisticsdeliverysystem.entity;

import java.time.LocalDateTime;

/**
 * 配送员，对应 postman 表。
 * <p>
 * 与 Operator 分开建表：配送员是被分派的对象，业务档案与操作员不同；
 * 但他同样需要登录，凭据放在本表，登录入口与操作员分开。
 */
public class Postman
{
    private Long id;
    private String username;
    private String passwordHash;
    private String salt;
    private String name;
    private String phone;
    private Long stationId;
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

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPasswordHash()
    {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash)
    {
        this.passwordHash = passwordHash;
    }

    public String getSalt()
    {
        return salt;
    }

    public void setSalt(String salt)
    {
        this.salt = salt;
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
